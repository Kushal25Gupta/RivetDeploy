package com.rivetdeploy.backend.events;

import java.time.Instant;

public record DeploymentEventDto(
        Long id,
        String deploymentId,
        String eventType,
        String message,
        Instant timestamp
) {
    public static DeploymentEventDto from(DeploymentEvent event) {
        return new DeploymentEventDto(
                event.getId(),
                event.getDeployment().getId(),
                event.getEventType(),
                event.getMessage(),
                event.getTimestamp()
        );
    }
}
