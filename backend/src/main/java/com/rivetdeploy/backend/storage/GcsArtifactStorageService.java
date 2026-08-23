package com.rivetdeploy.backend.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(name = "rivetdeploy.storage.type", havingValue = "gcs")
public class GcsArtifactStorageService implements ArtifactStorageService {

    private static final Logger log = LoggerFactory.getLogger(GcsArtifactStorageService.class);

    private final String bucketName;
    private final Storage storage;

    public GcsArtifactStorageService(@Value("${rivetdeploy.storage.gcs-bucket:rivetdeploy-artifacts}") String bucketName) {
        this.bucketName = bucketName;
        this.storage = StorageOptions.getDefaultInstance().getService();
    }

    @Override
    public String uploadArtifacts(String projectId, String deploymentId, File sourceDir, String configuredOutputDir) throws IOException {
        String prefix = String.format("projects/%s/deployments/%s/", projectId, deploymentId);
        File outputDir = resolveOutputDirectory(sourceDir);
        log.info("Uploading artifacts from {} to GCS gs://{}/{}", outputDir.getAbsolutePath(), bucketName, prefix);

        Path sourcePath = outputDir.toPath();
        try (var stream = Files.walk(sourcePath)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                String relativePath = sourcePath.relativize(file).toString().replace("\\", "/");
                String blobName = prefix + relativePath;
                try {
                    BlobId blobId = BlobId.of(bucketName, blobName);
                    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
                    storage.create(blobInfo, Files.readAllBytes(file));
                } catch (IOException e) {
                    log.error("Failed to upload file {} to GCS", file, e);
                    throw new RuntimeException("GCS upload failed", e);
                }
            });
        }

        return String.format("gs://%s/%s", bucketName, prefix);
    }

    @Override
    public void activateDeployment(String projectId, String deploymentId) {
        // In GCS / Cloud CDN model, active deployment is tracked in DB or metadata manifest
        log.info("Setting GCS active deployment pointer for project {} to {}", projectId, deploymentId);
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
}
