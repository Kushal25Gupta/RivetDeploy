package com.rivetdeploy.backend.deployment;

import com.rivetdeploy.backend.project.Project;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "deployments")
public class Deployment {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "commit_sha", nullable = false)
    private String commitSha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeploymentState status;

    @Column(name = "failure_type")
    private String failureType;

    @Column(name = "artifact_location")
    private String artifactLocation;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Deployment() {}

    public void transitionTo(DeploymentState nextState) {
        if (!this.status.canTransitionTo(nextState)) {
            throw new IllegalStateException("Invalid state transition from " + this.status + " to " + nextState);
        }
        this.status = nextState;
        
        if (nextState == DeploymentState.CLONING) {
            this.startedAt = Instant.now();
        } else if (nextState.isTerminal()) {
            this.completedAt = Instant.now();
        }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }

    public DeploymentState getStatus() { return status; }
    public void setStatus(DeploymentState status) { this.status = status; }

    public String getFailureType() { return failureType; }
    public void setFailureType(String failureType) { this.failureType = failureType; }

    public String getArtifactLocation() { return artifactLocation; }
    public void setArtifactLocation(String artifactLocation) { this.artifactLocation = artifactLocation; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
