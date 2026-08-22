package com.rivetdeploy.backend.executor;

import com.rivetdeploy.backend.deployment.Deployment;
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
    private final TransactionTemplate transactionTemplate;
    private final com.rivetdeploy.backend.git.GitService gitService;
    private final com.rivetdeploy.backend.docker.DockerBuildService dockerBuildService;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isRunning = true;

    public WorkerService(JobQueue jobQueue, DeploymentRepository deploymentRepository, TransactionTemplate transactionTemplate,
                         com.rivetdeploy.backend.git.GitService gitService, com.rivetdeploy.backend.docker.DockerBuildService dockerBuildService) {
        this.jobQueue = jobQueue;
        this.deploymentRepository = deploymentRepository;
        this.transactionTemplate = transactionTemplate;
        this.gitService = gitService;
        this.dockerBuildService = dockerBuildService;
    }

    @PostConstruct
    public void startWorker() {
        executor.submit(this::workerLoop);
        log.info("Single build worker started.");
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

                log.info("Worker claimed job for deployment: {}", job.getDeploymentId());
                processJob(job);
                jobQueue.acknowledge(job);
            } catch (Exception e) {
                log.error("Worker loop encountered an error", e);
            }
        }
    }

    private void processJob(Job job) {
        try {
            // Fetch deployment to get repo URL and commit SHA
            Deployment deployment = deploymentRepository.findById(job.getDeploymentId())
                    .orElseThrow(() -> new IllegalArgumentException("Deployment not found: " + job.getDeploymentId()));
            String repoUrl = deployment.getProject().getRepositoryUrl();
            String commitSha = deployment.getCommitSha();

            // Transition to CLONING
            updateDeploymentState(job.getDeploymentId(), DeploymentState.CLONING);
            java.io.File workDir = gitService.cloneRepository(repoUrl, commitSha, job.getDeploymentId());

            // Transition to INSTALLING (e.g. Nixpacks preparing dependencies)
            updateDeploymentState(job.getDeploymentId(), DeploymentState.INSTALLING);

            // Transition to BUILDING
            updateDeploymentState(job.getDeploymentId(), DeploymentState.BUILDING);
            String imageTag = dockerBuildService.buildImage(job.getDeploymentId(), workDir);

            // In Phase 1/2 we just consider BUILDING and DEPLOYED for simplicity, skipping UPLOADING to registry for now
            // Transition to UPLOADING
            updateDeploymentState(job.getDeploymentId(), DeploymentState.UPLOADING);

            // Transition to DEPLOYED
            updateDeploymentState(job.getDeploymentId(), DeploymentState.DEPLOYED);
            log.info("Deployment {} completed successfully with image {}", job.getDeploymentId(), imageTag);

        } catch (Exception e) {
            log.error("Failed to process deployment {}", job.getDeploymentId(), e);
            updateDeploymentState(job.getDeploymentId(), DeploymentState.SYSTEM_FAILED);
        }
    }

    private void updateDeploymentState(String deploymentId, DeploymentState state) {
        transactionTemplate.executeWithoutResult(status -> {
            Deployment deployment = deploymentRepository.findById(deploymentId)
                    .orElseThrow(() -> new IllegalArgumentException("Deployment not found: " + deploymentId));
            deployment.transitionTo(state);
            deploymentRepository.save(deployment);
            log.debug("Deployment {} transitioned to {}", deploymentId, state);
        });
    }

    private void simulateWork(long millis) throws InterruptedException {
        // This will be replaced by actual Docker execution in Step 11/12
        Thread.sleep(millis);
    }
}
