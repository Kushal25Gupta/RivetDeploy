package com.rivetdeploy.backend.events;

import com.rivetdeploy.backend.deployment.Deployment;
import com.rivetdeploy.backend.deployment.DeploymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventLoggerService {
    
    private static final Logger log = LoggerFactory.getLogger(EventLoggerService.class);
    
    private final DeploymentEventRepository eventRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogWebSocketHandler webSocketHandler;

    public EventLoggerService(DeploymentEventRepository eventRepository, 
                              DeploymentRepository deploymentRepository,
                              DeploymentLogWebSocketHandler webSocketHandler) {
        this.eventRepository = eventRepository;
        this.deploymentRepository = deploymentRepository;
        this.webSocketHandler = webSocketHandler;
    }

    // Requires NEW transaction so logs are saved even if the outer build transaction rolls back
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String deploymentId, String eventType, String message) {
        deploymentRepository.findById(deploymentId).ifPresentOrElse(deployment -> {
            DeploymentEvent event = new DeploymentEvent(deployment, eventType, message);
            DeploymentEvent saved = eventRepository.save(event);
            log.debug("Event logged for {}: [{}] {}", deploymentId, eventType, message);
            
            // Broadcast via WebSocket
            webSocketHandler.broadcastEvent(deploymentId, DeploymentEventDto.from(saved));
        }, () -> {
            log.warn("Cannot log event. Deployment {} not found.", deploymentId);
        });
    }
}
