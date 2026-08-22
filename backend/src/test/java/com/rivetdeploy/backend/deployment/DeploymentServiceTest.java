package com.rivetdeploy.backend.deployment;

import com.rivetdeploy.backend.events.DeploymentEventRepository;
import com.rivetdeploy.backend.events.EventLoggerService;
import com.rivetdeploy.backend.project.Project;
import com.rivetdeploy.backend.project.ProjectRepository;
import com.rivetdeploy.backend.scheduler.JobQueue;
import com.rivetdeploy.backend.storage.ArtifactStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeploymentServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private JobQueue jobQueue;

    @Mock
    private ArtifactStorageService artifactStorageService;

    @Mock
    private EventLoggerService eventLoggerService;

    @Mock
    private DeploymentEventRepository eventRepository;

    @Mock
    private com.rivetdeploy.backend.executor.CancellationManager cancellationManager;

    @Mock
    private com.rivetdeploy.backend.observability.MetricsService metricsService;

    private DeploymentService deploymentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        deploymentService = new DeploymentService(
                deploymentRepository,
                projectRepository,
                jobQueue,
                artifactStorageService,
                eventLoggerService,
                eventRepository,
                cancellationManager,
                metricsService
        );
    }

    @Test
    void testCreateDeployment() {
        String projectId = "prj-1";
        String ownerId = "user-1";
        Project project = new Project();
        project.setId(projectId);
        project.setOwnerId(ownerId);

        when(projectRepository.findByIdAndOwnerId(projectId, ownerId)).thenReturn(Optional.of(project));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(i -> i.getArgument(0));

        Deployment result = deploymentService.createDeployment(projectId, "abcdef1234567890", ownerId);

        assertNotNull(result);
        assertEquals(DeploymentState.QUEUED, result.getStatus());
        assertEquals("abcdef1234567890", result.getCommitSha());
        verify(jobQueue, times(1)).enqueue(any());
        verify(eventLoggerService, times(1)).logEvent(eq(result.getId()), eq("QUEUED"), any());
    }

    @Test
    void testRollbackSuccessful() throws IOException {
        String deploymentId = "dpl-old";
        String ownerId = "user-1";
        String projectId = "prj-1";

        Project project = new Project();
        project.setId(projectId);
        project.setOwnerId(ownerId);
        project.setActiveDeploymentId("dpl-current");

        Deployment oldDeployment = new Deployment();
        oldDeployment.setId(deploymentId);
        oldDeployment.setProject(project);
        oldDeployment.setStatus(DeploymentState.DEPLOYED);

        when(deploymentRepository.findByIdAndProject_OwnerId(deploymentId, ownerId)).thenReturn(Optional.of(oldDeployment));
        when(projectRepository.save(any(Project.class))).thenAnswer(i -> i.getArgument(0));

        Project updatedProject = deploymentService.rollback(deploymentId, ownerId);

        assertEquals(deploymentId, updatedProject.getActiveDeploymentId());
        verify(artifactStorageService, times(1)).activateDeployment(projectId, deploymentId);
        verify(eventLoggerService, times(1)).logEvent(eq(deploymentId), eq("ROLLBACK"), any());
    }

    @Test
    void testRollbackFailsForNonDeployedState() {
        String deploymentId = "dpl-failed";
        String ownerId = "user-1";
        String projectId = "prj-1";

        Project project = new Project();
        project.setId(projectId);
        project.setOwnerId(ownerId);

        Deployment failedDeployment = new Deployment();
        failedDeployment.setId(deploymentId);
        failedDeployment.setProject(project);
        failedDeployment.setStatus(DeploymentState.BUILD_FAILED);

        when(deploymentRepository.findByIdAndProject_OwnerId(deploymentId, ownerId)).thenReturn(Optional.of(failedDeployment));

        assertThrows(IllegalStateException.class, () -> {
            deploymentService.rollback(deploymentId, ownerId);
        });
    }

    @Test
    void testCancelQueuedDeployment() {
        String deploymentId = "dpl-queued";
        String ownerId = "user-1";
        String projectId = "prj-1";

        Project project = new Project();
        project.setId(projectId);
        project.setOwnerId(ownerId);

        Deployment deployment = new Deployment();
        deployment.setId(deploymentId);
        deployment.setProject(project);
        deployment.setStatus(DeploymentState.QUEUED);

        when(deploymentRepository.findByIdAndProject_OwnerId(deploymentId, ownerId)).thenReturn(Optional.of(deployment));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(i -> i.getArgument(0));

        Deployment cancelled = deploymentService.cancelDeployment(deploymentId, ownerId);

        assertEquals(DeploymentState.CANCELLED, cancelled.getStatus());
        assertEquals("USER_CANCELLED", cancelled.getFailureType());
        verify(cancellationManager, times(1)).requestCancellation(deploymentId);
        verify(eventLoggerService, times(1)).logEvent(eq(deploymentId), eq("CANCELLED"), any());
    }

    @Test
    void testCancelTerminalDeploymentThrows() {
        String deploymentId = "dpl-terminal";
        String ownerId = "user-1";
        String projectId = "prj-1";

        Project project = new Project();
        project.setId(projectId);
        project.setOwnerId(ownerId);

        Deployment deployment = new Deployment();
        deployment.setId(deploymentId);
        deployment.setProject(project);
        deployment.setStatus(DeploymentState.DEPLOYED);

        when(deploymentRepository.findByIdAndProject_OwnerId(deploymentId, ownerId)).thenReturn(Optional.of(deployment));

        assertThrows(IllegalStateException.class, () -> {
            deploymentService.cancelDeployment(deploymentId, ownerId);
        });
    }
}
