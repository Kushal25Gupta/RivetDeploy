package com.rivetdeploy.backend.scheduler;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class InMemoryJobQueue implements JobQueue {

    private final BlockingQueue<Job> queue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Override
    public void enqueue(Job job) {
        queue.add(job);
    }

    @Override
    public Job dequeue() {
        try {
            // Take blocks until an element is available.
            // Using poll(1, TimeUnit.SECONDS) could also work, but take() is strictly FIFO blocking.
            return queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public void acknowledge(Job job) {
        // In-memory queue doesn't need explicit ack as take() removes it,
        // but Redis will need this to remove from processing set.
    }

    @Override
    public void requeue(Job job, Duration delay) {
        if (delay == null || delay.isZero()) {
            enqueue(job);
        } else {
            scheduler.schedule(() -> enqueue(job), delay.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public int size() {
        return queue.size();
    }
}
