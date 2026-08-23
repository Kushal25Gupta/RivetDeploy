package com.rivetdeploy.backend.api;

import com.rivetdeploy.backend.auth.User;
import com.rivetdeploy.backend.auth.UserRepository;
import com.rivetdeploy.backend.project.Project;
import com.rivetdeploy.backend.project.ProjectRequest;
import com.rivetdeploy.backend.project.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final UserRepository userRepository;

    public ProjectController(ProjectService projectService, UserRepository userRepository) {
        this.projectService = projectService;
        this.userRepository = userRepository;
    }

    private Optional<User> getAuthenticatedUser(OAuth2User principal) {
        if (principal == null) return Optional.empty();
        return userRepository.findByGithubId(principal.getName());
    }

    @PostMapping
    public ResponseEntity<Project> createProject(@AuthenticationPrincipal OAuth2User principal, @RequestBody ProjectRequest request) {
        Optional<User> user = getAuthenticatedUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        Project project = projectService.createProject(user.get().getId(), request);
        return ResponseEntity.ok(project);
    }

    @GetMapping
    public ResponseEntity<List<Project>> listProjects(@AuthenticationPrincipal OAuth2User principal) {
        Optional<User> user = getAuthenticatedUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(projectService.getProjectsForUser(user.get().getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> getProject(@AuthenticationPrincipal OAuth2User principal, @PathVariable String id) {
        Optional<User> user = getAuthenticatedUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        return projectService.getProjectForUser(id, user.get().getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@AuthenticationPrincipal OAuth2User principal, @PathVariable String id) {
        Optional<User> user = getAuthenticatedUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        try {
            projectService.deleteProject(id, user.get().getId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<Project> suspendProject(@AuthenticationPrincipal OAuth2User principal, @PathVariable String id) {
        Optional<User> user = getAuthenticatedUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        try {
            Project project = projectService.suspendProject(id, user.get().getId());
            return ResponseEntity.ok(project);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Project> resumeProject(@AuthenticationPrincipal OAuth2User principal, @PathVariable String id) {
        Optional<User> user = getAuthenticatedUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        try {
            Project project = projectService.resumeProject(id, user.get().getId());
            return ResponseEntity.ok(project);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
