package com.rivetdeploy.backend.deployment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeploymentStateTest {

    @Test
    void testValidTransitions() {
        Deployment deployment = new Deployment();
        deployment.setStatus(DeploymentState.QUEUED);

        assertDoesNotThrow(() -> deployment.transitionTo(DeploymentState.CLONING));
        assertEquals(DeploymentState.CLONING, deployment.getStatus());
        assertNotNull(deployment.getStartedAt());

        assertDoesNotThrow(() -> deployment.transitionTo(DeploymentState.INSTALLING));
        assertDoesNotThrow(() -> deployment.transitionTo(DeploymentState.BUILDING));
        assertDoesNotThrow(() -> deployment.transitionTo(DeploymentState.UPLOADING));
        assertDoesNotThrow(() -> deployment.transitionTo(DeploymentState.DEPLOYED));
        
        assertNotNull(deployment.getCompletedAt());
    }

    @Test
    void testInvalidTransitions() {
        Deployment deployment = new Deployment();
        deployment.setStatus(DeploymentState.QUEUED);

        // Cannot jump directly from QUEUED to DEPLOYED
        assertThrows(IllegalStateException.class, () -> deployment.transitionTo(DeploymentState.DEPLOYED));
        
        // Cannot jump from CLONING to BUILDING
        deployment.setStatus(DeploymentState.CLONING);
        assertThrows(IllegalStateException.class, () -> deployment.transitionTo(DeploymentState.BUILDING));
    }

    @Test
    void testTerminalStatesCannotTransition() {
        Deployment deployment = new Deployment();
        deployment.setStatus(DeploymentState.BUILD_FAILED);

        // Cannot transition from terminal state
        assertThrows(IllegalStateException.class, () -> deployment.transitionTo(DeploymentState.UPLOADING));
        assertThrows(IllegalStateException.class, () -> deployment.transitionTo(DeploymentState.CANCELLED));
    }
}
