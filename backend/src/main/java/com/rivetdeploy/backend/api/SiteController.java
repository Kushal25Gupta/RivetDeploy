package com.rivetdeploy.backend.api;

import com.rivetdeploy.backend.project.Project;
import com.rivetdeploy.backend.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLConnection;

@RestController
@RequestMapping("/sites")
@ConditionalOnProperty(name = "rivetdeploy.storage.type", havingValue = "s3")
public class SiteController {

    private static final Logger log = LoggerFactory.getLogger(SiteController.class);

    private final ProjectRepository projectRepository;
    private final S3Client s3Client;
    private final String bucketName;

    public SiteController(ProjectRepository projectRepository, S3Client s3Client,
                          @Value("${rivetdeploy.storage.s3-bucket:rivetdeploy-artifacts}") String bucketName) {
        this.projectRepository = projectRepository;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @GetMapping("/projects/{projectId}/current/**")
    public ResponseEntity<?> serveSiteFile(@PathVariable String projectId, HttpServletRequest request) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null || project.getActiveDeploymentId() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Project or active deployment not found");
        }

        if (project.getIsSuspended()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("This project has been suspended by the owner.");
        }

        String deploymentId = project.getActiveDeploymentId();

        // Extract the remaining path after /sites/projects/{projectId}/current/
        String uri = request.getRequestURI();
        String prefix = "/sites/projects/" + projectId + "/current/";
        String filePath = uri.substring(uri.indexOf(prefix) + prefix.length());

        if (filePath.isEmpty() || filePath.endsWith("/")) {
            filePath += "index.html";
        }

        String objectKey = String.format("projects/%s/deployments/%s/%s", projectId, deploymentId, filePath);

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            var responseInputStream = s3Client.getObject(getObjectRequest);

            String contentType = URLConnection.guessContentTypeFromName(filePath);
            if (contentType == null) {
                if (filePath.endsWith(".css")) contentType = "text/css";
                else if (filePath.endsWith(".js")) contentType = "application/javascript";
                else contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(new InputStreamResource(responseInputStream));
        } catch (NoSuchKeyException e) {
            log.warn("S3 object not found: {}", objectKey);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found in deployment");
        } catch (Exception e) {
            log.error("Error serving file from S3: {}", objectKey, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
