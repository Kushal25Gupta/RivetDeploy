package com.rivetdeploy.backend.deployment;

public class TransientFailureException extends RuntimeException {
    private final FailureType failureType;

    public TransientFailureException(FailureType failureType, String message) {
        super(message);
        this.failureType = failureType;
    }

    public TransientFailureException(FailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }

    public FailureType getFailureType() {
        return failureType;
    }
}
