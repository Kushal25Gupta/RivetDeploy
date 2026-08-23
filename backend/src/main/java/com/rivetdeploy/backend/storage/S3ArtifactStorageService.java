package com.rivetdeploy.backend.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(name = "rivetdeploy.storage.type", havingValue = "s3")
public class S3ArtifactStorageService implements ArtifactStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3ArtifactStorageService.class);

    private final String bucketName;
    private final S3Client s3Client;

    public S3ArtifactStorageService(
            @Value("${rivetdeploy.storage.s3-bucket:rivetdeploy-artifacts}") String bucketName,
            S3Client s3Client) {
        this.bucketName = bucketName;
        this.s3Client = s3Client;
    }

    @Override
    public String uploadArtifacts(String projectId, String deploymentId, File sourceDir, String configuredOutputDir) throws IOException {
        String prefix = String.format("projects/%s/deployments/%s/", projectId, deploymentId);
        File outputDir = resolveOutputDirectory(sourceDir, configuredOutputDir);
        log.info("Uploading artifacts from {} to S3 s3://{}/{}", outputDir.getAbsolutePath(), bucketName, prefix);

        Path sourcePath = outputDir.toPath();
        try (var stream = Files.walk(sourcePath)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                String relativePath = sourcePath.relativize(file).toString().replace("\\", "/");
                String objectKey = prefix + relativePath;
                try {
                    PutObjectRequest putOb = PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .build();

                    s3Client.putObject(putOb, RequestBody.fromFile(file));
                } catch (Exception e) {
                    log.error("Failed to upload file {} to S3", file, e);
                    throw new RuntimeException("S3 upload failed", e);
                }
            });
        }

        return String.format("s3://%s/%s", bucketName, prefix);
    }

    @Override
    public void activateDeployment(String projectId, String deploymentId) {
        // In S3 model, active deployment is tracked in DB or metadata manifest
        log.info("Setting S3 active deployment pointer for project {} to {}", projectId, deploymentId);
    }

    private File resolveOutputDirectory(File root, String configuredOutputDir) {
        String[] candidateDirs = {configuredOutputDir, "dist", "build", "out", "public"};
        for (String candidate : candidateDirs) {
            if (candidate == null || candidate.isEmpty()) continue;
            File dir = new File(root, candidate);
            if (dir.exists() && dir.isDirectory()) {
                return dir;
            }
        }
        return root;
    }
}
