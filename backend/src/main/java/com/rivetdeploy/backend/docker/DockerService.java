package com.rivetdeploy.backend.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;

@Service
public class DockerService {

    private static final Logger log = LoggerFactory.getLogger(DockerService.class);
    private DockerClient dockerClient;

    @PostConstruct
    public void init() {
        log.info("Initializing Docker client...");
        
        // This configures the connection to the host Docker daemon
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
        
        try {
            dockerClient.pingCmd().exec();
            log.info("Successfully connected to Docker daemon.");
        } catch (Exception e) {
            log.error("Failed to connect to Docker daemon.", e);
            // In a real scenario we might throw an exception, but for now we log it.
        }
    }

    public DockerClient getClient() {
        return dockerClient;
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (dockerClient != null) {
                dockerClient.close();
            }
        } catch (IOException e) {
            log.error("Error closing Docker client", e);
        }
    }
}
