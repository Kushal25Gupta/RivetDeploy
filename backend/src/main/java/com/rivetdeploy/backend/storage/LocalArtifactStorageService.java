package com.rivetdeploy.backend.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Service
@ConditionalOnProperty(name = "rivetdeploy.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalArtifactStorageService implements ArtifactStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalArtifactStorageService.class);

    private final Path baseStoragePath;

    public LocalArtifactStorageService(@Value("${rivetdeploy.storage.local-dir:/tmp/rivetdeploy/artifacts}") String localDir) {
        this.baseStoragePath = Paths.get(localDir);
        try {
            Files.createDirectories(baseStoragePath);
        } catch (IOException e) {
            log.error("Failed to create base artifact storage directory: {}", localDir, e);
        }
    }

    @Override
    public String uploadArtifacts(String projectId, String deploymentId, File sourceDir) throws IOException {
        Path targetDir = baseStoragePath.resolve("projects").resolve(projectId).resolve("deployments").resolve(deploymentId);
        Files.createDirectories(targetDir);

        File outputDir = resolveOutputDirectory(sourceDir);
        log.info("Copying build artifacts from {} to immutable prefix: {}", outputDir.getAbsolutePath(), targetDir.toAbsolutePath());

        copyDirectory(outputDir.toPath(), targetDir);

        return targetDir.toAbsolutePath().toString();
    }

    @Override
    public void activateDeployment(String projectId, String deploymentId) throws IOException {
        Path projectPath = baseStoragePath.resolve("projects").resolve(projectId);
        Files.createDirectories(projectPath);
        
        Path deploymentPath = projectPath.resolve("deployments").resolve(deploymentId);
        Path currentLink = projectPath.resolve("current");

        if (!Files.exists(deploymentPath)) {
            throw new IllegalArgumentException("Cannot activate deployment. Directory does not exist: " + deploymentPath);
        }

        // Delete existing symlink or folder if present
        if (Files.isSymbolicLink(currentLink) || Files.exists(currentLink)) {
            Files.delete(currentLink);
        }

        // Create symlink to active deployment
        Files.createSymbolicLink(currentLink, deploymentPath);
        log.info("Activated deployment {} for project {}: {} -> {}", deploymentId, projectId, currentLink, deploymentPath);
    }

    private File resolveOutputDirectory(File root) {
        String[] candidateDirs = {"dist", "build", "out", "public"};
        for (String candidate : candidateDirs) {
            File dir = new File(root, candidate);
            if (dir.exists() && dir.isDirectory()) {
                return dir;
            }
        }
        return root;
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
