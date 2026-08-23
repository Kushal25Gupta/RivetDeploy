package com.rivetdeploy.backend.executor;

import com.rivetdeploy.backend.deployment.Deployment;
import com.rivetdeploy.backend.deployment.DeploymentRepository;
import com.rivetdeploy.backend.deployment.DeploymentState;
import com.rivetdeploy.backend.deployment.FailureClassifier;
import com.rivetdeploy.backend.deployment.FailureType;
import com.rivetdeploy.backend.deployment.PermanentFailureException;
import com.rivetdeploy.backend.deployment.TransientFailureException;
import com.rivetdeploy.backend.scheduler.Job;
import com.rivetdeploy.backend.scheduler.JobQueue;
import com.rivetdeploy.backend.scheduler.RetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WorkerServiceTest {

    @Mock
    private JobQueue jobQueue;

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private com.rivetdeploy.backend.project.ProjectRepository projectRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private com.rivetdeploy.backend.git.GitService gitService;

    @Mock
    private com.rivetdeploy.backend.docker.DockerBuildService dockerBuildService;

    @Mock
    private com.rivetdeploy.backend.storage.ArtifactStorageService artifactStorageService;

    @Mock
    private com.rivetdeploy.backend.events.EventLoggerService eventLoggerService;

    @Mock
    private CancellationManager cancellationManager;

    @Mock
    private com.rivetdeploy.backend.observability.MetricsService metricsService;

    private FailureClassifier failureClassifier;
    private RetryPolicy retryPolicy;
    private WorkerService workerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        failureClassifier = new FailureClassifier();
        retryPolicy = new RetryPolicy(3, 10, 100, 2.0); // fast retry for test
        
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        workerService = new WorkerService(
                jobQueue, 
                deploymentRepository, 
                projectRepository, 
                transactionTemplate, 
                gitService, 
                dockerBuildService, 
                artifactStorageService, 
                eventLoggerService, 
                failureClassifier, 
                retryPolicy,
                cancellationManager,
                metricsService,
                1
        );
    }

    @Test
    void testWorkerProcessesJobSuccessfully() throws InterruptedException {
        String deploymentId = "dpl_123";
        Job job = new Job(deploymentId);
        
        com.rivetdeploy.backend.project.Project project = new com.rivetdeploy.backend.project.Project();
        project.setId("prj_123");
        project.setRepositoryUrl("https://github.com/dummy/repo.git");
        
        Deployment deployment = new Deployment();
        deployment.setId(deploymentId);
        deployment.setProject(project);
        deployment.setCommitSha("HEAD");
        deployment.setStatus(DeploymentState.QUEUED);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean jobReturned = new AtomicBoolean(false);

        when(jobQueue.dequeue()).thenAnswer(invocation -> {
            if (!jobReturned.get()) {
                jobReturned.set(true);
                return job;
            }
            Thread.sleep(5000);
            return null;
        });

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(jobQueue).acknowledge(any());

        try {
            when(gitService.cloneRepository(any(), any(), any(), any())).thenReturn(new java.io.File("/tmp/dummy"));
            when(dockerBuildService.buildImage(any(), any())).thenReturn("rivetdeploy-app:dpl_123");
            when(artifactStorageService.uploadArtifacts(any(), any(), any())).thenReturn("projects/prj_123/deployments/dpl_123");
            when(projectRepository.findById(any())).thenReturn(Optional.of(project));
        } catch (Exception e) {}

        workerService.startWorker();

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        workerService.stopWorker();

        assertTrue(completed, "Worker did not reach DEPLOYED state in time");
        assertEquals(DeploymentState.DEPLOYED, deployment.getStatus());
        verify(jobQueue, times(1)).acknowledge(job);
    }

    @Test
    void testWorkerRetriesOnTransientFailure() throws Exception {
        String deploymentId = "dpl_transient";
        Job job = new Job(deploymentId);

        com.rivetdeploy.backend.project.Project project = new com.rivetdeploy.backend.project.Project();
        project.setId("prj_123");
        project.setRepositoryUrl("https://github.com/dummy/repo.git");

        Deployment deployment = new Deployment();
        deployment.setId(deploymentId);
        deployment.setProject(project);
        deployment.setStatus(DeploymentState.QUEUED);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean jobReturned = new AtomicBoolean(false);

        when(jobQueue.dequeue()).thenAnswer(invocation -> {
            if (!jobReturned.get()) {
                jobReturned.set(true);
                return job;
            }
            Thread.sleep(5000);
            return null;
        });

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(gitService.cloneRepository(any(), any(), any(), any()))
                .thenThrow(new TransientFailureException(FailureType.TRANSIENT_NETWORK, "Connection timeout"));

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(jobQueue).requeue(eq(job), any(Duration.class));

        workerService.startWorker();

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        workerService.stopWorker();

        assertTrue(completed, "Worker did not requeue transiently failed job");
        assertEquals(1, job.getRetryCount());
        verify(jobQueue, times(1)).requeue(eq(job), any(Duration.class));
    }

    @Test
    void testWorkerFailsPermanentlyOnNonRetryableError() throws Exception {
        String deploymentId = "dpl_perm_fail";
        Job job = new Job(deploymentId);

        com.rivetdeploy.backend.project.Project project = new com.rivetdeploy.backend.project.Project();
        project.setId("prj_123");
        project.setRepositoryUrl("https://github.com/dummy/repo.git");

        Deployment deployment = new Deployment();
        deployment.setId(deploymentId);
        deployment.setProject(project);
        deployment.setStatus(DeploymentState.QUEUED);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean jobReturned = new AtomicBoolean(false);

        when(jobQueue.dequeue()).thenAnswer(invocation -> {
            if (!jobReturned.get()) {
                jobReturned.set(true);
                return job;
            }
            Thread.sleep(5000);
            return null;
        });

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(gitService.cloneRepository(any(), any(), any(), any())).thenReturn(new java.io.File("/tmp/dummy"));
        when(dockerBuildService.buildImage(any(), any()))
                .thenThrow(new PermanentFailureException(FailureType.BUILD_COMMAND_FAILED, "Build exited with status 1"));

        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> {
            Deployment saved = invocation.getArgument(0);
            if (saved.getStatus() == DeploymentState.BUILD_FAILED) {
                latch.countDown();
            }
            return saved;
        });

        workerService.startWorker();

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        workerService.stopWorker();

        assertTrue(completed, "Worker did not mark deployment as BUILD_FAILED");
        assertEquals(DeploymentState.BUILD_FAILED, deployment.getStatus());
        assertEquals("BUILD_COMMAND_FAILED", deployment.getFailureType());
        verify(jobQueue, never()).requeue(any(), any());
    }

    @Test
    void testWorkerStopsOnCancellation() throws Exception {
        String deploymentId = "dpl_cancelled";
        Job job = new Job(deploymentId);

        com.rivetdeploy.backend.project.Project project = new com.rivetdeploy.backend.project.Project();
        project.setId("prj_123");
        project.setRepositoryUrl("https://github.com/dummy/repo.git");

        Deployment deployment = new Deployment();
        deployment.setId(deploymentId);
        deployment.setProject(project);
        deployment.setStatus(DeploymentState.QUEUED);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean jobReturned = new AtomicBoolean(false);

        when(jobQueue.dequeue()).thenAnswer(invocation -> {
            if (!jobReturned.get()) {
                jobReturned.set(true);
                return job;
            }
            Thread.sleep(5000);
            return null;
        });

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(cancellationManager.isCancelled(deploymentId)).thenReturn(true);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(jobQueue).acknowledge(job);

        workerService.startWorker();

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        workerService.stopWorker();

        assertTrue(completed, "Worker did not acknowledge cancelled job");
        verify(gitService, never()).cloneRepository(any(), any(), any());
    }
}
