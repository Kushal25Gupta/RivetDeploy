package com.rivetdeploy.backend.project;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project createProject(String ownerId, ProjectRequest request) {
        Project project = new Project();
        project.setId(UUID.randomUUID().toString());
        project.setOwnerId(ownerId);
        project.setName(request.name());
        project.setRepositoryUrl(request.repositoryUrl());
        project.setBranch(request.branch());
        project.setCreatedAt(Instant.now());
        project.setUpdatedAt(Instant.now());
        return projectRepository.save(project);
    }

    public List<Project> getProjectsForUser(String ownerId) {
        return projectRepository.findByOwnerId(ownerId);
    }

    public Optional<Project> getProjectForUser(String projectId, String ownerId) {
        return projectRepository.findByIdAndOwnerId(projectId, ownerId);
    }

    public void deleteProject(String projectId, String ownerId) {
        Project project = getProjectForUser(projectId, ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        projectRepository.delete(project);
    }

    public Project suspendProject(String projectId, String ownerId) {
        Project project = getProjectForUser(projectId, ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        project.setIsSuspended(true);
        return projectRepository.save(project);
    }

    public Project resumeProject(String projectId, String ownerId) {
        Project project = getProjectForUser(projectId, ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        project.setIsSuspended(false);
        return projectRepository.save(project);
    }
}
