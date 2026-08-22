package com.rivetdeploy.backend.events;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeploymentEventRepository extends JpaRepository<DeploymentEvent, Long> {
    List<DeploymentEvent> findByDeploymentIdOrderByTimestampAsc(String deploymentId);
}
