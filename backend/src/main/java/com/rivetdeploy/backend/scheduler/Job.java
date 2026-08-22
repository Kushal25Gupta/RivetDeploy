package com.rivetdeploy.backend.scheduler;

public class Job {
    private String deploymentId;
    private int retryCount;

    public Job() {}

    public Job(String deploymentId) {
        this.deploymentId = deploymentId;
        this.retryCount = 0;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }
}
