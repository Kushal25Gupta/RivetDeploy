package com.rivetdeploy.backend.git;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    public File cloneRepository(String repositoryUrl, String commitSha, String deploymentId) throws IOException, InterruptedException {
        File workDir = new File("/tmp/rivetdeploy/builds/" + deploymentId);
        
        if (workDir.exists()) {
            cleanupWorkspace(workDir);
        }
        
        workDir.mkdirs();

        log.info("Cloning repository {} into {}", repositoryUrl, workDir.getAbsolutePath());

        // Shallow clone single branch
        ProcessBuilder clonePb = new ProcessBuilder(
                "git", "clone", "--depth", "1", repositoryUrl, "."
        );
        clonePb.directory(workDir);
        clonePb.inheritIO();
        
        Process cloneProcess = clonePb.start();
        int exitCode = cloneProcess.waitFor();
        
        if (exitCode != 0) {
            throw new RuntimeException("Git clone failed with exit code " + exitCode);
        }

        // If a specific commit is requested, we need to fetch it and checkout
        if (commitSha != null && !commitSha.isEmpty() && !commitSha.equals("HEAD")) {
            log.info("Checking out commit {}", commitSha);
            ProcessBuilder fetchPb = new ProcessBuilder("git", "fetch", "origin", commitSha);
            fetchPb.directory(workDir);
            fetchPb.inheritIO();
            if (fetchPb.start().waitFor() != 0) {
                throw new RuntimeException("Git fetch failed for commit " + commitSha);
            }

            ProcessBuilder checkoutPb = new ProcessBuilder("git", "checkout", commitSha);
            checkoutPb.directory(workDir);
            checkoutPb.inheritIO();
            if (checkoutPb.start().waitFor() != 0) {
                throw new RuntimeException("Git checkout failed for commit " + commitSha);
            }
        }

        return workDir;
    }

    public boolean cleanupWorkspace(File directoryToBeDeleted) {
        if (directoryToBeDeleted == null || !directoryToBeDeleted.exists()) {
            return true;
        }
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                cleanupWorkspace(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
}
