package com.rivetdeploy.backend.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.model.BuildResponseItem;
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

    public DockerBuildService(DockerService dockerService) {
        this.dockerService = dockerService;
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
                                log.info("[BUILD {}] {}", deploymentId, item.getStream().trim());
                            } else if (item.getErrorDetail() != null) {
                                log.error("[BUILD {}] ERROR: {}", deploymentId, item.getErrorDetail().getMessage());
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
            pb.inheritIO(); // stream to backend stdout (which will eventually pipe to DB)
            
            Process process = pb.start();
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
