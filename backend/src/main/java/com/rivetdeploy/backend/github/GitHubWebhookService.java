package com.rivetdeploy.backend.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rivetdeploy.backend.deployment.Deployment;
import com.rivetdeploy.backend.deployment.DeploymentService;
import com.rivetdeploy.backend.project.Project;
import com.rivetdeploy.backend.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class GitHubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookService.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String webhookSecret;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final ProjectRepository projectRepository;
    private final DeploymentService deploymentService;
    private final ObjectMapper objectMapper;

    public GitHubWebhookService(
            @Value("${rivetdeploy.github.webhook-secret:dev-secret}") String webhookSecret,
            WebhookDeliveryRepository webhookDeliveryRepository,
            ProjectRepository projectRepository,
            DeploymentService deploymentService) {
        this.webhookSecret = webhookSecret;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.projectRepository = projectRepository;
        this.deploymentService = deploymentService;
        this.objectMapper = new ObjectMapper();
    }

    public boolean verifySignature(String payload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        String expectedSignature = signatureHeader.substring("sha256=".length());

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String actualSignature = HexFormat.of().formatHex(hash);

            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    actualSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to verify HMAC signature", e);
            return false;
        }
    }

    @Transactional
    public String handleWebhook(String deliveryId, String eventType, String payload) {
        if (deliveryId != null && webhookDeliveryRepository.existsByDeliveryId(deliveryId)) {
            log.info("Duplicate webhook delivery {} ignored for idempotency.", deliveryId);
            return "Duplicate event ignored";
        }

        if ("ping".equalsIgnoreCase(eventType)) {
            if (deliveryId != null) {
                webhookDeliveryRepository.save(new WebhookDelivery(deliveryId, eventType, null));
            }
            return "PONG";
        }

        if (!"push".equalsIgnoreCase(eventType)) {
            log.info("Ignoring unhandled event type: {}", eventType);
            return "Event ignored";
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String ref = root.path("ref").asText(); // e.g. "refs/heads/main"
            String branch = ref.replace("refs/heads/", "");
            String commitSha = root.path("after").asText();
            String cloneUrl = root.path("repository").path("clone_url").asText();
            String htmlUrl = root.path("repository").path("html_url").asText();

            if (commitSha == null || commitSha.isEmpty() || commitSha.equals("0000000000000000000000000000000000000000")) {
                return "Deleted branch, no deployment triggered";
            }

            // Find matching project
            List<Project> allProjects = projectRepository.findAll();
            Optional<Project> matchingProject = allProjects.stream()
                    .filter(p -> normalizeUrl(p.getRepositoryUrl()).equals(normalizeUrl(cloneUrl)) ||
                                 normalizeUrl(p.getRepositoryUrl()).equals(normalizeUrl(htmlUrl)))
                    .filter(p -> p.getBranch().equals(branch))
                    .findFirst();

            if (matchingProject.isEmpty()) {
                log.warn("No matching project found for repo {} and branch {}", cloneUrl, branch);
                if (deliveryId != null) {
                    webhookDeliveryRepository.save(new WebhookDelivery(deliveryId, eventType, null));
                }
                return "No matching project";
            }

            Project project = matchingProject.get();
            Deployment deployment = deploymentService.createDeployment(project.getId(), commitSha, project.getOwnerId());
            log.info("Triggered auto-deployment {} for project {}", deployment.getId(), project.getName());

            if (deliveryId != null) {
                webhookDeliveryRepository.save(new WebhookDelivery(deliveryId, eventType, deployment.getId()));
            }

            return "Deployment triggered: " + deployment.getId();
        } catch (Exception e) {
            log.error("Error processing GitHub webhook", e);
            throw new RuntimeException("Webhook processing error", e);
        }
    }

    private String normalizeUrl(String url) {
        if (url == null) return "";
        return url.toLowerCase().replaceAll("\\.git$", "").replaceAll("/+$", "");
    }
}
