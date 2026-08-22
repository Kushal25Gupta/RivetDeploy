package com.rivetdeploy.backend.storage;

import java.io.File;
import java.io.IOException;

public interface ArtifactStorageService {
    /**
     * Uploads the build artifact files from source directory into an immutable storage prefix:
     * e.g., projects/{projectId}/deployments/{deploymentId}/
     * 
     * @return The canonical URI/path of the stored artifact
     */
    String uploadArtifacts(String projectId, String deploymentId, File sourceDir) throws IOException;

    /**
     * Points the active deployment for the project to the given deploymentId
     */
    void activateDeployment(String projectId, String deploymentId) throws IOException;
}
