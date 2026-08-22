CREATE TABLE webhook_deliveries (
    delivery_id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deployment_id VARCHAR(255) REFERENCES deployments(id) ON DELETE SET NULL
);
