package com.rivetdeploy.backend.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class DeploymentLogWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DeploymentLogWebSocketHandler.class);
    private final Map<String, Set<WebSocketSession>> deploymentSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String deploymentId = extractDeploymentId(session.getUri());
        if (deploymentId != null) {
            deploymentSessions.computeIfAbsent(deploymentId, k -> new CopyOnWriteArraySet<>()).add(session);
            log.info("WebSocket connected for deployment {}: session {}", deploymentId, session.getId());
        } else {
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String deploymentId = extractDeploymentId(session.getUri());
        if (deploymentId != null) {
            Set<WebSocketSession> sessions = deploymentSessions.get(deploymentId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    deploymentSessions.remove(deploymentId);
                }
            }
        }
        log.info("WebSocket closed for session: {}", session.getId());
    }

    public void broadcastEvent(String deploymentId, Object event) {
        Set<WebSocketSession> sessions = deploymentSessions.get(deploymentId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            TextMessage message = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        synchronized (session) {
                            session.sendMessage(message);
                        }
                    } catch (IOException e) {
                        log.error("Failed to send WS message to session {}", session.getId(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error serializing event for WS broadcast", e);
        }
    }

    private String extractDeploymentId(URI uri) {
        if (uri == null || uri.getPath() == null) return null;
        String path = uri.getPath();
        String prefix = "/ws/deployments/";
        if (path.startsWith(prefix) && path.length() > prefix.length()) {
            return path.substring(prefix.length()).replaceAll("/.*", "");
        }
        return null;
    }
}
