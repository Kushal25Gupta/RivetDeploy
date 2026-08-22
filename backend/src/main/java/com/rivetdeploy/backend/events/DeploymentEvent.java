package com.rivetdeploy.backend.events;

import com.rivetdeploy.backend.deployment.Deployment;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "deployment_events")
public class DeploymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id", nullable = false)
    private Deployment deployment;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    private String message;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    public DeploymentEvent() {}

    public DeploymentEvent(Deployment deployment, String eventType, String message) {
        this.deployment = deployment;
        this.eventType = eventType;
        this.message = message;
        this.timestamp = Instant.now();
    }

    // Getters
    public Long getId() { return id; }
    public Deployment getDeployment() { return deployment; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
}
