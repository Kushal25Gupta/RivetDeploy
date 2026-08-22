package com.rivetdeploy.backend.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MetricsService {

    private final Counter deploymentTotal;
    private final Counter deploymentSuccessTotal;
    private final Counter deploymentFailureTotal;
    private final Counter retryTotal;
    private final Counter cancelTotal;
    private final Counter resourceLimitViolations;
    private final AtomicInteger activeWorkers;
    private final Timer queueWaitTimer;
    private final Timer buildDurationTimer;
    private final Timer deploymentDurationTimer;

    public MetricsService(MeterRegistry registry) {
        this.deploymentTotal = registry.counter("deployment_total");
        this.deploymentSuccessTotal = registry.counter("deployment_success_total");
        this.deploymentFailureTotal = registry.counter("deployment_failure_total");
        this.retryTotal = registry.counter("retry_total");
        this.cancelTotal = registry.counter("cancel_total");
        this.resourceLimitViolations = registry.counter("resource_limit_violations");

        this.activeWorkers = registry.gauge("active_workers", new AtomicInteger(0));

        this.queueWaitTimer = Timer.builder("queue_wait_seconds")
                .description("Time spent waiting in queue before execution")
                .register(registry);
        this.buildDurationTimer = Timer.builder("build_duration_seconds")
                .description("Duration of the build phase")
                .register(registry);
        this.deploymentDurationTimer = Timer.builder("deployment_duration_seconds")
                .description("Total duration from start to completion")
                .register(registry);
    }

    public void incrementDeploymentTotal() {
        deploymentTotal.increment();
    }

    public void incrementDeploymentSuccess() {
        deploymentSuccessTotal.increment();
    }

    public void incrementDeploymentFailure() {
        deploymentFailureTotal.increment();
    }

    public void incrementRetryTotal() {
        retryTotal.increment();
    }

    public void incrementCancelTotal() {
        cancelTotal.increment();
    }

    public void incrementResourceLimitViolations() {
        resourceLimitViolations.increment();
    }

    public void workerStarted() {
        activeWorkers.incrementAndGet();
    }

    public void workerFinished() {
        activeWorkers.decrementAndGet();
    }

    public void recordQueueWait(Duration duration) {
        queueWaitTimer.record(duration);
    }

    public void recordBuildDuration(Duration duration) {
        buildDurationTimer.record(duration);
    }

    public void recordDeploymentDuration(Duration duration) {
        deploymentDurationTimer.record(duration);
    }
}
