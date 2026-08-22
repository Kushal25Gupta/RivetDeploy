package com.rivetdeploy.backend.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.rivetdeploy.backend.events.EventLoggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

@Service
public class DockerBuildService {

    private static final Logger log = LoggerFactory.getLogger(DockerBuildService.class);
    private final DockerService dockerService;
    private final EventLoggerService eventLoggerService;

    public DockerBuildService(DockerService dockerService, EventLoggerService eventLoggerService) {
        this.dockerService = dockerService;
        this.eventLoggerService = eventLoggerService;
    }

    public String buildImage(String deploymentId, File sourceDirectory) {
        DockerClient dockerClient = dockerService.getClient();
        String imageTag = "rivetdeploy-app:" + deploymentId;
        
        log.info("Starting build for {} from {}", imageTag, sourceDirectory.getAbsolutePath());

        File dockerfile = new File(sourceDirectory, "Dockerfile");
        
        if (dockerfile.exists()) {
            log.info("Dockerfile found. Executing Docker build...");
            return buildWithDockerClient(dockerClient, deploymentId, sourceDirectory, imageTag);
        } else {
            log.info("No Dockerfile found. Falling back to Nixpacks...");
            return buildWithNixpacks(deploymentId, sourceDirectory, imageTag);
        }
    }

    private String buildWithDockerClient(DockerClient dockerClient, String deploymentId, File sourceDirectory, String imageTag) {
        Set<String> tags = new HashSet<>();
        tags.add(imageTag);

        try {
            String imageId = dockerClient.buildImageCmd(sourceDirectory)
                    .withTags(tags)
                    .exec(new BuildImageResultCallback() {
                        @Override
                        public void onNext(BuildResponseItem item) {
                            if (item.getStream() != null) {
                                String msg = item.getStream().trim();
                                log.info("[BUILD {}] {}", deploymentId, msg);
                                eventLoggerService.logEvent(deploymentId, "BUILD_LOG", msg);
                            } else if (item.getErrorDetail() != null) {
                                String errorMsg = item.getErrorDetail().getMessage();
                                log.error("[BUILD {}] ERROR: {}", deploymentId, errorMsg);
                                eventLoggerService.logEvent(deploymentId, "BUILD_ERROR_LOG", errorMsg);
                            }
                            super.onNext(item);
                        }
                    })
                    .awaitImageId();

            log.info("Successfully built image {} with ID {}", imageTag, imageId);
            return imageTag;
        } catch (Exception e) {
            log.error("Docker build failed for {}", deploymentId, e);
            throw new RuntimeException("Docker build failed", e);
        }
    }

    private String buildWithNixpacks(String deploymentId, File sourceDirectory, String imageTag) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "nixpacks", "build", ".", "--name", imageTag
            );
            pb.directory(sourceDirectory);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[NIXPACKS {}] {}", deploymentId, line);
                    eventLoggerService.logEvent(deploymentId, "BUILD_LOG", line);
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                throw new RuntimeException("Nixpacks build failed with exit code " + exitCode);
            }
            
            log.info("Successfully built image {} via Nixpacks", imageTag);
            return imageTag;
        } catch (Exception e) {
            log.error("Nixpacks build failed for {}", deploymentId, e);
            throw new RuntimeException("Nixpacks build failed", e);
        }
    }
}
