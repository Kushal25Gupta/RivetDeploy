package com.rivetdeploy.backend.deployment;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

@Component
public class FailureClassifier {

    public FailureType classify(Throwable throwable) {
        if (throwable == null) {
            return FailureType.SYSTEM_ERROR;
        }

        if (throwable instanceof TransientFailureException tfe) {
            return tfe.getFailureType();
        }

        if (throwable instanceof PermanentFailureException pfe) {
            return pfe.getFailureType();
        }

        // Check root causes
        Throwable root = getRootCause(throwable);

        if (root instanceof SocketTimeoutException || root instanceof SocketException) {
            return FailureType.TRANSIENT_NETWORK;
        }

        String msg = root.getMessage() != null ? root.getMessage().toLowerCase() : "";

        if (msg.contains("connection refused") || msg.contains("connection reset") || msg.contains("timeout")) {
            return FailureType.TRANSIENT_NETWORK;
        }

        if (msg.contains("clone failed") || msg.contains("could not resolve host") || msg.contains("remote repository not found")) {
            return FailureType.CLONE_FAILED;
        }

        if (msg.contains("docker build failed") || msg.contains("nixpacks build failed")) {
            return FailureType.BUILD_COMMAND_FAILED;
        }

        if (msg.contains("storage") || msg.contains("gcs") || msg.contains("gcs upload failed")) {
            return FailureType.TRANSIENT_STORAGE_FAILURE;
        }

        return FailureType.SYSTEM_ERROR;
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
