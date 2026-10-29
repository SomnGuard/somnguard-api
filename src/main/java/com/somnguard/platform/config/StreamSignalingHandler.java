package com.somnguard.platform.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.somnguard.device_management.application.usecase.StreamSessionService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Signaling HU-API-012 (ADR-013 Propuesta) + estado en tiempo real.
 * Relay por session_id: reenvía offer/answer/ice/subscribe/stop/frame entre Pi y viewer.
 * Valida la existencia y vigencia de session_id con StreamSessionService antes de relay.
 */
@Component
public class StreamSignalingHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(StreamSignalingHandler.class);
    private static final Set<String> TYPES = Set.of(
            "offer", "answer", "ice", "subscribe", "stop", "wants-view",
            "frame", "ping", "hello", "request-offer", "subscribe-status", "unsubscribe-status");

    private final ObjectMapper mapper = new ObjectMapper();
    private final StreamSessionService streamSessionService;
    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> deviceWatchers = new ConcurrentHashMap<>();

    public StreamSignalingHandler(StreamSessionService streamSessionService) {
        this.streamSessionService = streamSessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LOG.debug("WS stream conectada {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root;
        try {
            root = mapper.readTree(message.getPayload());
        } catch (Exception e) {
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"JSON inválido\"}"));
            return;
        }
        String type = root.path("type").asText("");
        if (!TYPES.contains(type)) {
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"type desconocido\"}"));
            return;
        }
        // Watch de estado por device (tiempo real, sin session_id).
        if ("subscribe-status".equals(type) || "unsubscribe-status".equals(type)) {
            String deviceId = root.path("device_id").asText("");
            if (deviceId.isBlank()) {
                session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"device_id requerido\"}"));
                return;
            }
            if ("subscribe-status".equals(type)) {
                deviceWatchers.computeIfAbsent(deviceId, k -> ConcurrentHashMap.newKeySet()).add(session);
                session.sendMessage(new TextMessage(
                        "{\"type\":\"subscribed-status\",\"device_id\":\"" + deviceId + "\"}"));
            } else {
                Set<WebSocketSession> watchers = deviceWatchers.get(deviceId);
                if (watchers != null) {
                    watchers.remove(session);
                }
            }
            return;
        }
        String sessionId = root.path("session_id").asText("");
        if (sessionId.isBlank()) {
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"session_id requerido\"}"));
            return;
        }

        // Validación de sesión activa (H-09)
        try {
            UUID sUuid = UUID.fromString(sessionId);
            if (!streamSessionService.isValidActiveSession(sUuid)) {
                session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"Sesión de streaming no válida o expirada\"}"));
                return;
            }
        } catch (IllegalArgumentException ex) {
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"session_id UUID inválido\"}"));
            return;
        }

        rooms.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(session);
        broadcast(sessionId, message.getPayload(), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        for (Set<WebSocketSession> members : rooms.values()) {
            members.remove(session);
        }
        for (Set<WebSocketSession> watchers : deviceWatchers.values()) {
            watchers.remove(session);
        }
    }

    /** Push de estado a los viewers suscritos al device (tiempo real). */
    public void broadcastStatus(String deviceId, String payload) {
        Set<WebSocketSession> watchers = deviceWatchers.getOrDefault(deviceId, Set.of());
        for (WebSocketSession s : watchers) {
            if (!s.isOpen()) {
                continue;
            }
            try {
                s.sendMessage(new TextMessage(payload));
            } catch (Exception e) {
                LOG.debug("Status WS {} falló: {}", deviceId, e.getMessage());
            }
        }
    }

    private void broadcast(String sessionId, String payload, WebSocketSession sender) {
        Set<WebSocketSession> members = rooms.getOrDefault(sessionId, Set.of());
        for (WebSocketSession s : members) {
            if (s.equals(sender) || !s.isOpen()) {
                continue;
            }
            try {
                s.sendMessage(new TextMessage(payload));
            } catch (Exception e) {
                LOG.debug("Relay WS {} falló: {}", sessionId, e.getMessage());
            }
        }
    }
}
