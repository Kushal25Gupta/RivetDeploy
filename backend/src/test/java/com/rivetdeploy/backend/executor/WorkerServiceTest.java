package com.rivetdeploy.backend.executor;

import com.rivetdeploy.backend.deployment.Deployment;
import com.rivetdeploy.backend.deployment.DeploymentRepository;
import com.rivetdeploy.backend.deployment.DeploymentState;
import com.rivetdeploy.backend.scheduler.Job;
import com.rivetdeploy.backend.scheduler.JobQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkerServiceTest {

    @Mock
    private JobQueue jobQueue;

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private WorkerService workerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock transaction template to just execute the callback
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        workerService = new WorkerService(jobQueue, deploymentRepository, transactionTemplate);
    }

    @Test
    void testWorkerProcessesJobSuccessfully() throws InterruptedException {
        String deploymentId = "dpl_123";
        Job job = new Job(deploymentId);
        Deployment deployment = new Deployment();
        deployment.setId(deploymentId);
        deployment.setStatus(DeploymentState.QUEUED);

        CountDownLatch latch = new CountDownLatch(1);

        when(jobQueue.dequeue()).thenAnswer(invocation -> {
            // First call returns the job, second call blocks until latch or interrupted
            if (latch.getCount() == 1) {
                return job;
            }
            Thread.sleep(10000); // block subsequent calls
            return null;
        });

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        
        // Use answer on repository save to countdown the latch when DEPLOYED state is reached
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> {
            Deployment saved = invocation.getArgument(0);
            if (saved.getStatus() == DeploymentState.DEPLOYED) {
                latch.countDown();
            }
            return saved;
        });

        workerService.startWorker();

        // Wait for the worker to finish processing (up to 10 seconds for the simulated Thread.sleep calls)
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        workerService.stopWorker();

        assertEquals(true, completed, "Worker did not reach DEPLOYED state in time");
        assertEquals(DeploymentState.DEPLOYED, deployment.getStatus());
        verify(jobQueue, times(1)).acknowledge(job);
    }
}
