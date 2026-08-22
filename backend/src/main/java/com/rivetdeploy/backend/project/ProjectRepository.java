package com.rivetdeploy.backend.project;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, String> {
    List<Project> findByOwnerId(String ownerId);
    Optional<Project> findByIdAndOwnerId(String id, String ownerId);
}
