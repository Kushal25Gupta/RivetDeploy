package com.rivetdeploy.backend.deployment;

public class PermanentFailureException extends RuntimeException {
    private final FailureType failureType;

    public PermanentFailureException(FailureType failureType, String message) {
        super(message);
        this.failureType = failureType;
    }

    public PermanentFailureException(FailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }

    public FailureType getFailureType() {
        return failureType;
    }
}
