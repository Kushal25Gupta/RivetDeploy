package com.rivetdeploy.backend.deployment;

public enum DeploymentState {
    QUEUED,
    CLONING,
    INSTALLING,
    BUILDING,
    UPLOADING,
    DEPLOYED,
    
    // Terminal states
    CLONE_FAILED,
    INSTALL_FAILED,
    BUILD_FAILED,
    UPLOAD_FAILED,
    TIMEOUT,
    SYSTEM_FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == DEPLOYED ||
               this == CLONE_FAILED ||
               this == INSTALL_FAILED ||
               this == BUILD_FAILED ||
               this == UPLOAD_FAILED ||
               this == TIMEOUT ||
               this == SYSTEM_FAILED ||
               this == CANCELLED;
    }

    public boolean canTransitionTo(DeploymentState nextState) {
        if (this.isTerminal()) {
            return false; // Terminal states cannot transition to anything
        }

        return switch (this) {
            case QUEUED -> nextState == CLONING || nextState == CANCELLED || nextState == SYSTEM_FAILED;
            case CLONING -> nextState == INSTALLING || nextState == QUEUED || nextState == CLONE_FAILED || nextState == SYSTEM_FAILED || nextState == CANCELLED;
            case INSTALLING -> nextState == BUILDING || nextState == QUEUED || nextState == INSTALL_FAILED || nextState == SYSTEM_FAILED || nextState == CANCELLED;
            case BUILDING -> nextState == UPLOADING || nextState == QUEUED || nextState == BUILD_FAILED || nextState == TIMEOUT || nextState == SYSTEM_FAILED || nextState == CANCELLED;
            case UPLOADING -> nextState == DEPLOYED || nextState == QUEUED || nextState == UPLOAD_FAILED || nextState == SYSTEM_FAILED || nextState == CANCELLED;
            default -> false;
        };
    }
}
