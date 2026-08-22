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

    public DeploymentService(DeploymentRepository deploymentRepository, ProjectRepository projectRepository, JobQueue jobQueue) {
        this.deploymentRepository = deploymentRepository;
        this.projectRepository = projectRepository;
        this.jobQueue = jobQueue;
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
        jobQueue.enqueue(new Job(saved.getId()));
        return saved;
    }

    @Transactional
    public void transitionDeploymentState(String deploymentId, DeploymentState newState) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new IllegalArgumentException("Deployment not found"));
        
        deployment.transitionTo(newState);
        deploymentRepository.save(deployment);
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
}
