package com.rivetdeploy.backend.executor;

import com.rivetdeploy.backend.docker.DockerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CancellationManager {

    private static final Logger log = LoggerFactory.getLogger(CancellationManager.class);

    private final Set<String> cancelledDeployments = ConcurrentHashMap.newKeySet();
    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final Map<String, String> activeContainers = new ConcurrentHashMap<>();
    private final DockerService dockerService;

    public CancellationManager(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    public void requestCancellation(String deploymentId) {
        cancelledDeployments.add(deploymentId);
        log.info("Cancellation requested for deployment: {}", deploymentId);
        cancelActive(deploymentId);
    }

    public boolean isCancelled(String deploymentId) {
        return cancelledDeployments.contains(deploymentId);
    }

    public void registerProcess(String deploymentId, Process process) {
        activeProcesses.put(deploymentId, process);
    }

    public void registerContainer(String deploymentId, String containerId) {
        activeContainers.put(deploymentId, containerId);
    }

    public void unregister(String deploymentId) {
        activeProcesses.remove(deploymentId);
        activeContainers.remove(deploymentId);
        cancelledDeployments.remove(deploymentId);
    }

    public void cancelActive(String deploymentId) {
        Process process = activeProcesses.remove(deploymentId);
        if (process != null && process.isAlive()) {
            log.info("Destroying process for cancelled deployment {}", deploymentId);
            process.destroyForcibly();
        }

        String containerId = activeContainers.remove(deploymentId);
        if (containerId != null) {
            try {
                log.info("Stopping and removing container {} for cancelled deployment {}", containerId, deploymentId);
                dockerService.getClient().stopContainerCmd(containerId).withTimeout(5).exec();
                dockerService.getClient().removeContainerCmd(containerId).withForce(true).exec();
            } catch (Exception e) {
                log.warn("Failed to stop/remove container {} on cancellation: {}", containerId, e.getMessage());
            }
        }
    }
}
