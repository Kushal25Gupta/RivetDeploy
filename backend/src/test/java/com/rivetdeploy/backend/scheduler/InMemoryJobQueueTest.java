package com.rivetdeploy.backend.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryJobQueueTest {

    private InMemoryJobQueue jobQueue;

    @BeforeEach
    void setUp() {
        jobQueue = new InMemoryJobQueue();
    }

    @Test
    void testFifoBehavior() {
        Job job1 = new Job("dpl_1");
        Job job2 = new Job("dpl_2");
        Job job3 = new Job("dpl_3");

        jobQueue.enqueue(job1);
        jobQueue.enqueue(job2);
        jobQueue.enqueue(job3);

        assertEquals(3, jobQueue.size());

        assertEquals("dpl_1", jobQueue.dequeue().getDeploymentId());
        assertEquals("dpl_2", jobQueue.dequeue().getDeploymentId());
        assertEquals("dpl_3", jobQueue.dequeue().getDeploymentId());
        
        assertEquals(0, jobQueue.size());
    }

    @Test
    void testRequeueWithDelay() throws InterruptedException {
        Job job1 = new Job("dpl_1");
        
        jobQueue.requeue(job1, Duration.ofMillis(100));
        
        // Immediately it should be 0
        assertEquals(0, jobQueue.size());
        
        // Wait for schedule
        Thread.sleep(150);
        
        assertEquals(1, jobQueue.size());
        assertEquals("dpl_1", jobQueue.dequeue().getDeploymentId());
    }
}
