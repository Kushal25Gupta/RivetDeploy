package com.rivetdeploy.backend.github;

import com.rivetdeploy.backend.deployment.Deployment;
import com.rivetdeploy.backend.deployment.DeploymentService;
import com.rivetdeploy.backend.project.Project;
import com.rivetdeploy.backend.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GitHubWebhookServiceTest {

    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private DeploymentService deploymentService;

    private GitHubWebhookService webhookService;
    private final String secret = "test-secret";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        webhookService = new GitHubWebhookService(secret, webhookDeliveryRepository, projectRepository, deploymentService);
    }

    private String computeHmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testVerifySignatureSuccess() throws Exception {
        String payload = "{\"action\":\"push\"}";
        String sig = computeHmacSha256(payload, secret);
        assertTrue(webhookService.verifySignature(payload, sig));
    }

    @Test
    void testVerifySignatureFailure() throws Exception {
        String payload = "{\"action\":\"push\"}";
        String sig = computeHmacSha256(payload, "wrong-secret");
        assertFalse(webhookService.verifySignature(payload, sig));
    }

    @Test
    void testHandleWebhook_DuplicateDeliveryIgnored() {
        when(webhookDeliveryRepository.existsByDeliveryId("delivery-123")).thenReturn(true);

        String result = webhookService.handleWebhook("delivery-123", "push", "{}");

        assertEquals("Duplicate event ignored", result);
        verify(deploymentService, never()).createDeployment(any(), any(), any());
    }

    @Test
    void testHandleWebhook_PushEventCreatesDeployment() {
        String deliveryId = "delivery-456";
        String payload = """
        {
            "ref": "refs/heads/main",
            "after": "c0ffee1234567890",
            "repository": {
                "clone_url": "https://github.com/org/repo.git",
                "html_url": "https://github.com/org/repo"
            }
        }
        """;

        Project project = new Project();
        project.setId("prj-1");
        project.setOwnerId("user-1");
        project.setRepositoryUrl("https://github.com/org/repo.git");
        project.setBranch("main");

        when(webhookDeliveryRepository.existsByDeliveryId(deliveryId)).thenReturn(false);
        when(projectRepository.findAll()).thenReturn(List.of(project));

        Deployment deployment = new Deployment();
        deployment.setId("dpl-789");
        when(deploymentService.createDeployment("prj-1", "c0ffee1234567890", "user-1")).thenReturn(deployment);

        String result = webhookService.handleWebhook(deliveryId, "push", payload);

        assertEquals("Deployment triggered: dpl-789", result);
        verify(webhookDeliveryRepository, times(1)).save(any(WebhookDelivery.class));
    }
}
