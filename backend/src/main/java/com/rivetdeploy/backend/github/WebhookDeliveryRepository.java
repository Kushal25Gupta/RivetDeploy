package com.rivetdeploy.backend.github;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, String> {
    boolean existsByDeliveryId(String deliveryId);
}
