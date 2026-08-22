package com.rivetdeploy.backend.scheduler;

import java.time.Duration;

public interface JobQueue {
    void enqueue(Job job);
    Job dequeue();
    void acknowledge(Job job);
    void requeue(Job job, Duration delay);
    int size();
}
