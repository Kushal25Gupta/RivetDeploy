package com.rivetdeploy.backend.api;

import com.rivetdeploy.backend.github.GitHubWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class GitHubWebhookController {

    private final GitHubWebhookService webhookService;

    public GitHubWebhookController(GitHubWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "push") String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestBody String payload) {

        if (!webhookService.verifySignature(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        String result = webhookService.handleWebhook(deliveryId, eventType, payload);
        return ResponseEntity.ok(result);
    }
}
