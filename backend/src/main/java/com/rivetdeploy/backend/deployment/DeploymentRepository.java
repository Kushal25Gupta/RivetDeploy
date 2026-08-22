package com.rivetdeploy.backend.deployment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends JpaRepository<Deployment, String> {
    List<Deployment> findByProjectIdOrderByCreatedAtDesc(String projectId);
    Optional<Deployment> findByIdAndProject_OwnerId(String id, String ownerId);
}
