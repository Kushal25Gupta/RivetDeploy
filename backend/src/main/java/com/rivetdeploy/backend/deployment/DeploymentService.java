package com.rivetdeploy.backend.deployment;

import com.rivetdeploy.backend.project.Project;
import com.rivetdeploy.backend.project.ProjectRepository;
import com.rivetdeploy.backend.scheduler.Job;
import com.rivetdeploy.backend.scheduler.JobQueue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final ProjectRepository projectRepository;
    private final JobQueue jobQueue;
    private final com.rivetdeploy.backend.storage.ArtifactStorageService artifactStorageService;
    private final com.rivetdeploy.backend.events.EventLoggerService eventLoggerService;
    private final com.rivetdeploy.backend.events.DeploymentEventRepository eventRepository;
    private final com.rivetdeploy.backend.executor.CancellationManager cancellationManager;
    private final com.rivetdeploy.backend.observability.MetricsService metricsService;

    public DeploymentService(DeploymentRepository deploymentRepository, 
                             ProjectRepository projectRepository, 
                             JobQueue jobQueue,
                             com.rivetdeploy.backend.storage.ArtifactStorageService artifactStorageService,
                             com.rivetdeploy.backend.events.EventLoggerService eventLoggerService,
                             com.rivetdeploy.backend.events.DeploymentEventRepository eventRepository,
                             com.rivetdeploy.backend.executor.CancellationManager cancellationManager,
                             com.rivetdeploy.backend.observability.MetricsService metricsService) {
        this.deploymentRepository = deploymentRepository;
        this.projectRepository = projectRepository;
        this.jobQueue = jobQueue;
        this.artifactStorageService = artifactStorageService;
        this.eventLoggerService = eventLoggerService;
        this.eventRepository = eventRepository;
        this.cancellationManager = cancellationManager;
        this.metricsService = metricsService;
    }

    @Transactional
    public Deployment createDeployment(String projectId, String commitSha, String ownerId) {
        Project project = projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found or access denied"));

        Deployment deployment = new Deployment();
        deployment.setId("dpl_" + UUID.randomUUID().toString().replace("-", ""));
        deployment.setProject(project);
        deployment.setCommitSha(commitSha);
        deployment.setStatus(DeploymentState.QUEUED);
        deployment.setCreatedAt(Instant.now());

        Deployment saved = deploymentRepository.save(deployment);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override
            public void afterCommit() {
                jobQueue.enqueue(new Job(saved.getId()));
            }
        });
        eventLoggerService.logEvent(saved.getId(), "QUEUED", "Deployment queued for execution.");
        metricsService.incrementDeploymentTotal();
        return saved;
    }

    @Transactional
    public void transitionDeploymentState(String deploymentId, DeploymentState newState) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new IllegalArgumentException("Deployment not found"));
        
        deployment.transitionTo(newState);
        deploymentRepository.save(deployment);
        eventLoggerService.logEvent(deploymentId, "STATE_CHANGE", "Transitioned to " + newState.name());
    }

    @Transactional
    public Project rollback(String deploymentId, String ownerId) {
        Deployment deployment = deploymentRepository.findByIdAndProject_OwnerId(deploymentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Deployment not found or access denied"));

        if (deployment.getStatus() != DeploymentState.DEPLOYED) {
            throw new IllegalStateException("Cannot rollback to an incomplete or failed deployment.");
        }

        Project project = deployment.getProject();
        try {
            artifactStorageService.activateDeployment(project.getId(), deployment.getId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to reactivate artifact for deployment: " + deploymentId, e);
        }

        project.setActiveDeploymentId(deployment.getId());
        Project updated = projectRepository.save(project);
        eventLoggerService.logEvent(deployment.getId(), "ROLLBACK", "Rolled back to deployment " + deployment.getId());
        return updated;
    }

    public List<Deployment> getDeploymentsForProject(String projectId, String ownerId) {
        // verify ownership first
        projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found or access denied"));
        
        return deploymentRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public Optional<Deployment> getDeployment(String deploymentId, String ownerId) {
        return deploymentRepository.findByIdAndProject_OwnerId(deploymentId, ownerId);
    }

    public List<com.rivetdeploy.backend.events.DeploymentEvent> getDeploymentEvents(String deploymentId, String ownerId) {
        deploymentRepository.findByIdAndProject_OwnerId(deploymentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Deployment not found or access denied"));
        
        return eventRepository.findByDeploymentIdOrderByTimestampAsc(deploymentId);
    }

    @Transactional
    public Deployment cancelDeployment(String deploymentId, String ownerId) {
        Deployment deployment = deploymentRepository.findByIdAndProject_OwnerId(deploymentId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Deployment not found or access denied"));

        if (deployment.getStatus().isTerminal()) {
            throw new IllegalStateException("Deployment is already in terminal state: " + deployment.getStatus());
        }

        cancellationManager.requestCancellation(deploymentId);
        deployment.transitionTo(DeploymentState.CANCELLED);
        deployment.setFailureType(FailureType.USER_CANCELLED.name());
        Deployment saved = deploymentRepository.save(deployment);

        eventLoggerService.logEvent(deploymentId, "CANCELLED", "Deployment was cancelled by user.");
        return saved;
    }
}
