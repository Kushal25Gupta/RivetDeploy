package com.rivetdeploy.backend.github;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    @Column(name = "delivery_id")
    private String deliveryId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "deployment_id")
    private String deploymentId;

    public WebhookDelivery() {}

    public WebhookDelivery(String deliveryId, String eventType, String deploymentId) {
        this.deliveryId = deliveryId;
        this.eventType = eventType;
        this.deploymentId = deploymentId;
        this.processedAt = Instant.now();
    }

    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
}
