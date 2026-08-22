package com.rivetdeploy.backend.api;

import com.rivetdeploy.backend.auth.User;
import com.rivetdeploy.backend.auth.UserRepository;
import com.rivetdeploy.backend.deployment.Deployment;
import com.rivetdeploy.backend.deployment.DeploymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final UserRepository userRepository;

    public DeploymentController(DeploymentService deploymentService, UserRepository userRepository) {
        this.deploymentService = deploymentService;
        this.userRepository = userRepository;
    }

    private Optional<User> getAuthenticatedUser(OAuth2User principal) {
        if (principal == null) return Optional.empty();
        return userRepository.findByGithubId(principal.getName());
    }

    @PostMapping("/projects/{projectId}/deployments")
    public ResponseEntity<Deployment> createDeployment(
            @AuthenticationPrincipal OAuth2User principal, 
            @PathVariable String projectId, 
            @RequestParam String commitSha) {
        
        Optional<User> user = getAuthenticatedUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        try {
            Deployment deployment = deploymentService.createDeployment(projectId, commitSha, user.get().getId());
            return ResponseEntity.ok(deployment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build(); // Project not found or access denied
        }
    }

    @GetMapping("/projects/{projectId}/deployments")
    public ResponseEntity<List<Deployment>> listDeployments(
            @AuthenticationPrincipal OAuth2User principal, 
            @PathVariable String projectId) {
        
        Optional<User> user = getAuthenticatedUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        try {
            List<Deployment> deployments = deploymentService.getDeploymentsForProject(projectId, user.get().getId());
            return ResponseEntity.ok(deployments);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/deployments/{deploymentId}")
    public ResponseEntity<Deployment> getDeployment(
            @AuthenticationPrincipal OAuth2User principal, 
            @PathVariable String deploymentId) {
        
        Optional<User> user = getAuthenticatedUser(principal);
        if (user.isEmpty()) return ResponseEntity.status(401).build();

        return deploymentService.getDeployment(deploymentId, user.get().getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
