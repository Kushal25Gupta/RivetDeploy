package com.rivetdeploy.backend.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalArtifactStorageServiceTest {

    private LocalArtifactStorageService storageService;

    @TempDir
    Path tempStorageDir;

    @TempDir
    Path tempSourceDir;

    @BeforeEach
    void setUp() {
        storageService = new LocalArtifactStorageService(tempStorageDir.toString());
    }

    @Test
    void testUploadArtifacts_WithDistFolder() throws IOException {
        String projectId = "prj-test";
        String deploymentId = "dpl-001";

        // Create dummy dist/index.html in source
        Path distDir = tempSourceDir.resolve("dist");
        Files.createDirectories(distDir);
        Files.writeString(distDir.resolve("index.html"), "<h1>Hello RivetDeploy</h1>");

        String location = storageService.uploadArtifacts(projectId, deploymentId, tempSourceDir.toFile());

        Path targetIndex = tempStorageDir.resolve("projects").resolve(projectId).resolve("deployments").resolve(deploymentId).resolve("index.html");
        assertTrue(Files.exists(targetIndex));
        assertEquals("<h1>Hello RivetDeploy</h1>", Files.readString(targetIndex));
    }

    @Test
    void testActivateDeployment_CreatesSymlink() throws IOException {
        String projectId = "prj-test";
        String deploymentId = "dpl-001";

        Path deploymentDir = tempStorageDir.resolve("projects").resolve(projectId).resolve("deployments").resolve(deploymentId);
        Files.createDirectories(deploymentDir);
        Files.writeString(deploymentDir.resolve("index.html"), "<h1>Version 1</h1>");

        storageService.activateDeployment(projectId, deploymentId);

        Path currentLink = tempStorageDir.resolve("projects").resolve(projectId).resolve("current");
        assertTrue(Files.exists(currentLink));
        assertTrue(Files.isSymbolicLink(currentLink));
        assertEquals(deploymentDir, Files.readSymbolicLink(currentLink));
    }
}
