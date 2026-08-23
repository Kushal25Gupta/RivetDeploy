package com.rivetdeploy.backend.executor;

import com.rivetdeploy.backend.deployment.Deployment;
import com.rivetdeploy.backend.project.Project;
import com.rivetdeploy.backend.deployment.DeploymentRepository;
import com.rivetdeploy.backend.deployment.DeploymentState;
import com.rivetdeploy.backend.scheduler.Job;
import com.rivetdeploy.backend.scheduler.JobQueue;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final JobQueue jobQueue;
    private final DeploymentRepository deploymentRepository;
    private final com.rivetdeploy.backend.project.ProjectRepository projectRepository;
    private final TransactionTemplate transactionTemplate;
    private final com.rivetdeploy.backend.git.GitService gitService;
    private final com.rivetdeploy.backend.docker.DockerBuildService dockerBuildService;
    private final com.rivetdeploy.backend.storage.ArtifactStorageService artifactStorageService;
    private final com.rivetdeploy.backend.events.EventLoggerService eventLoggerService;
    private final com.rivetdeploy.backend.deployment.FailureClassifier failureClassifier;
    private final com.rivetdeploy.backend.scheduler.RetryPolicy retryPolicy;
    
    private final com.rivetdeploy.backend.executor.CancellationManager cancellationManager;
    private final com.rivetdeploy.backend.observability.MetricsService metricsService;
    
    private final int poolSize;
    private final ExecutorService executor;
    private volatile boolean isRunning = true;

    public WorkerService(JobQueue jobQueue, 
                         DeploymentRepository deploymentRepository, 
                         com.rivetdeploy.backend.project.ProjectRepository projectRepository,
                         TransactionTemplate transactionTemplate,
                         com.rivetdeploy.backend.git.GitService gitService, 
                         com.rivetdeploy.backend.docker.DockerBuildService dockerBuildService,
                         com.rivetdeploy.backend.storage.ArtifactStorageService artifactStorageService,
                         com.rivetdeploy.backend.events.EventLoggerService eventLoggerService,
                         com.rivetdeploy.backend.deployment.FailureClassifier failureClassifier,
                         com.rivetdeploy.backend.scheduler.RetryPolicy retryPolicy,
                         com.rivetdeploy.backend.executor.CancellationManager cancellationManager,
                         com.rivetdeploy.backend.observability.MetricsService metricsService,
                         @org.springframework.beans.factory.annotation.Value("${rivetdeploy.worker.pool-size:1}") int poolSize) {
        this.jobQueue = jobQueue;
        this.deploymentRepository = deploymentRepository;
        this.projectRepository = projectRepository;
        this.transactionTemplate = transactionTemplate;
        this.gitService = gitService;
        this.dockerBuildService = dockerBuildService;
        this.artifactStorageService = artifactStorageService;
        this.eventLoggerService = eventLoggerService;
        this.failureClassifier = failureClassifier;
        this.retryPolicy = retryPolicy;
        this.cancellationManager = cancellationManager;
        this.metricsService = metricsService;
        this.poolSize = poolSize;
        this.executor = Executors.newFixedThreadPool(poolSize);
    }

    @PostConstruct
    public void startWorker() {
        for (int i = 0; i < poolSize; i++) {
            executor.submit(this::workerLoop);
        }
        log.info("Worker pool started with {} worker(s).", poolSize);
    }

    @PreDestroy
    public void stopWorker() {
        isRunning = false;
        executor.shutdownNow();
    }

    private void workerLoop() {
        while (isRunning) {
            try {
                Job job = jobQueue.dequeue();
                if (job == null) continue; // interrupted

                log.info("Worker thread {} claimed job for deployment: {}", Thread.currentThread().getName(), job.getDeploymentId());
                metricsService.workerStarted();
                long startTime = System.currentTimeMillis();
                try {
                    processJob(job);
                } finally {
                    metricsService.workerFinished();
                    metricsService.recordDeploymentDuration(java.time.Duration.ofMillis(System.currentTimeMillis() - startTime));
                }
                jobQueue.acknowledge(job);
            } catch (Exception e) {
                log.error("Worker loop encountered an error", e);
            }
        }
    }

    private void processJob(Job job) {
        java.io.File workDir = null;
        try {
            if (cancellationManager.isCancelled(job.getDeploymentId())) {
                log.info("Deployment {} was cancelled before starting", job.getDeploymentId());
                return;
            }

            // Fetch deployment to get repo URL and commit SHA
            Deployment deployment = deploymentRepository.findById(job.getDeploymentId())
                    .orElseThrow(() -> new IllegalArgumentException("Deployment not found: " + job.getDeploymentId()));
            Project project = projectRepository.findById(deployment.getProject().getId()).orElseThrow();
            String repoUrl = project.getRepositoryUrl();
            String commitSha = deployment.getCommitSha();

            // Transition to CLONING
            updateDeploymentState(job.getDeploymentId(), DeploymentState.CLONING);
            String branch = project.getBranch();
            workDir = gitService.cloneRepository(repoUrl, branch != null ? branch : "main", commitSha, job.getDeploymentId());

            if (cancellationManager.isCancelled(job.getDeploymentId())) {
                log.info("Deployment {} was cancelled after clone", job.getDeploymentId());
                return;
            }

            // Transition to INSTALLING (e.g. Nixpacks preparing dependencies)
            updateDeploymentState(job.getDeploymentId(), DeploymentState.INSTALLING);

            // Transition to BUILDING
            updateDeploymentState(job.getDeploymentId(), DeploymentState.BUILDING);
            String imageTag = dockerBuildService.buildImage(job.getDeploymentId(), workDir);

            if (cancellationManager.isCancelled(job.getDeploymentId())) {
                log.info("Deployment {} was cancelled after build", job.getDeploymentId());
                return;
            }

            // Transition to UPLOADING
            updateDeploymentState(job.getDeploymentId(), DeploymentState.UPLOADING);
            
            java.io.File extractedDir = new java.io.File(workDir, ".rivetdeploy-extracted");
            extractedDir.mkdirs();
            
            String containerOut = "";
            if (containerOut == null || containerOut.isEmpty()) {
                containerOut = "/usr/share/nginx/html"; // default for nginx base images
            }
            
            String baseName = new java.io.File(containerOut).getName();
            try {
                dockerBuildService.extractArtifacts(imageTag, containerOut, extractedDir);
                log.info("Extracted artifacts from image {} path {} to {}", imageTag, containerOut, extractedDir.getAbsolutePath());
            } catch (Exception e) {
                log.warn("Failed to extract artifacts from container path {}, falling back to source dir upload: {}", containerOut, e.getMessage());
                extractedDir = workDir;
                baseName = "";
            }
            
            String artifactLocation = artifactStorageService.uploadArtifacts(deployment.getProject().getId(), job.getDeploymentId(), extractedDir, baseName);

            // Update deployment with artifact location
            transactionTemplate.executeWithoutResult(status -> {
                Deployment dep = deploymentRepository.findById(job.getDeploymentId()).orElseThrow();
                dep.setArtifactLocation(artifactLocation);
                deploymentRepository.save(dep);
            });

            // Activate deployment
            artifactStorageService.activateDeployment(deployment.getProject().getId(), job.getDeploymentId());
            transactionTemplate.executeWithoutResult(status -> {
                var proj = projectRepository.findById(deployment.getProject().getId()).orElseThrow();
                proj.setActiveDeploymentId(job.getDeploymentId());
                projectRepository.save(proj);
            });

            // Transition to DEPLOYED
            updateDeploymentState(job.getDeploymentId(), DeploymentState.DEPLOYED, null);
            log.info("Deployment {} completed successfully. Artifact: {}", job.getDeploymentId(), artifactLocation);
            eventLoggerService.logEvent(job.getDeploymentId(), "BUILD_SUCCESS", "Successfully deployed artifact to: " + artifactLocation);
            eventLoggerService.logEvent(job.getDeploymentId(), "ACTIVATED", "Project active deployment pointer updated.");
            metricsService.incrementDeploymentSuccess();

        } catch (Exception e) {
            if (cancellationManager.isCancelled(job.getDeploymentId())) {
                log.info("Deployment {} was cancelled during execution", job.getDeploymentId());
                metricsService.incrementCancelTotal();
                return;
            }

            log.error("Failed to process deployment {}", job.getDeploymentId(), e);
            com.rivetdeploy.backend.deployment.FailureType failureType = failureClassifier.classify(e);

            if (retryPolicy.shouldRetry(failureType, job.getRetryCount())) {
                job.incrementRetryCount();
                metricsService.incrementRetryTotal();
                java.time.Duration delay = retryPolicy.calculateBackoff(job.getRetryCount());
                log.info("Retrying deployment {} (attempt {}/{}) in {}ms due to {}", 
                        job.getDeploymentId(), job.getRetryCount(), retryPolicy.getMaxAttempts(), delay.toMillis(), failureType);
                
                eventLoggerService.logEvent(job.getDeploymentId(), "RETRY_SCHEDULED", 
                        String.format("Retry attempt %d scheduled in %d ms due to %s", job.getRetryCount(), delay.toMillis(), failureType));
                
                updateDeploymentState(job.getDeploymentId(), DeploymentState.QUEUED, failureType.name());
                jobQueue.requeue(job, delay);
            } else {
                metricsService.incrementDeploymentFailure();
                DeploymentState terminalState = mapFailureToState(failureType);
                updateDeploymentState(job.getDeploymentId(), terminalState, failureType.name());
                eventLoggerService.logEvent(job.getDeploymentId(), "BUILD_FAILED", "Deployment failed permanently: " + failureType.name() + " - " + e.getMessage());
            }
        } finally {
            if (workDir != null) {
                gitService.cleanupWorkspace(workDir);
            }
            cancellationManager.unregister(job.getDeploymentId());
        }
    }

    private DeploymentState mapFailureToState(com.rivetdeploy.backend.deployment.FailureType failureType) {
        return switch (failureType) {
            case CLONE_FAILED, INVALID_REPOSITORY -> DeploymentState.CLONE_FAILED;
            case BUILD_COMMAND_FAILED -> DeploymentState.BUILD_FAILED;
            case TIMEOUT_EXCEEDED -> DeploymentState.TIMEOUT;
            case USER_CANCELLED -> DeploymentState.CANCELLED;
            case TRANSIENT_STORAGE_FAILURE -> DeploymentState.UPLOAD_FAILED;
            default -> DeploymentState.SYSTEM_FAILED;
        };
    }

    private void updateDeploymentState(String deploymentId, DeploymentState state) {
        updateDeploymentState(deploymentId, state, null);
    }

    private void updateDeploymentState(String deploymentId, DeploymentState state, String failureType) {
        transactionTemplate.executeWithoutResult(status -> {
            Deployment deployment = deploymentRepository.findById(deploymentId)
                    .orElseThrow(() -> new IllegalArgumentException("Deployment not found: " + deploymentId));
            deployment.transitionTo(state);
            if (failureType != null) {
                deployment.setFailureType(failureType);
            }
            deploymentRepository.save(deployment);
            log.debug("Deployment {} transitioned to {}", deploymentId, state);
        });
        
        // Log event AFTER transaction completes successfully
        eventLoggerService.logEvent(deploymentId, "STATE_CHANGE", "Transitioned to " + state.name());
    }

    private void simulateWork(long millis) throws InterruptedException {
        // This will be replaced by actual Docker execution in Step 11/12
        Thread.sleep(millis);
    }
}
