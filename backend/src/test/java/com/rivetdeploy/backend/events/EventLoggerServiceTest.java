package com.rivetdeploy.backend.events;

import com.rivetdeploy.backend.deployment.Deployment;
import com.rivetdeploy.backend.deployment.DeploymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EventLoggerServiceTest {

    @Mock
    private DeploymentEventRepository eventRepository;

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private DeploymentLogWebSocketHandler webSocketHandler;

    private EventLoggerService eventLoggerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        eventLoggerService = new EventLoggerService(eventRepository, deploymentRepository, webSocketHandler);
    }

    @Test
    void testLogEventSuccessAndBroadcast() {
        String deploymentId = "dpl_123";
        Deployment deployment = new Deployment();
        deployment.setId(deploymentId);

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(eventRepository.save(any(DeploymentEvent.class))).thenAnswer(i -> i.getArgument(0));

        eventLoggerService.logEvent(deploymentId, "BUILD_LOG", "Starting build...");

        verify(eventRepository, times(1)).save(any(DeploymentEvent.class));
        verify(webSocketHandler, times(1)).broadcastEvent(eq(deploymentId), any(DeploymentEventDto.class));
    }
}
