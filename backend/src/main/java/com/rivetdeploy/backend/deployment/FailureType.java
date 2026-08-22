package com.rivetdeploy.backend.deployment;

public enum FailureType {
    // Transient (Retryable)
    TRANSIENT_NETWORK(true),
    TRANSIENT_DOCKER_ERROR(true),
    TRANSIENT_STORAGE_FAILURE(true),
    SYSTEM_ERROR(true),

    // Permanent (Non-retryable)
    INVALID_REPOSITORY(false),
    CLONE_FAILED(false),
    BUILD_COMMAND_FAILED(false),
    MALFORMED_OUTPUT_DIRECTORY(false),
    RESOURCE_LIMIT_EXCEEDED(false),
    TIMEOUT_EXCEEDED(false),
    USER_CANCELLED(false);

    private final boolean retryable;

    FailureType(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
