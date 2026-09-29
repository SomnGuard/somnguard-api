package com.somnguard.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WS signaling HU-API-012 (ADR-013 Propuesta). Sin STOMP en MVP: WS nativo + relay por session_id.
 */
@Configuration
@EnableWebSocket
public class StreamWebSocketConfig implements WebSocketConfigurer {

    private final StreamSignalingHandler handler;

    @Value("${app.stream.ws-path:/ws/stream}")
    private String wsPath;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:4200,http://localhost:8080}")
    private String allowedOrigins;

    public StreamWebSocketConfig(StreamSignalingHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, wsPath)
                .setAllowedOrigins(allowedOrigins.split(","));
    }

    /** Frames MJPEG 640x480 caben en ~80KB; se sube el tope (default 8KB daba 1009).
     *  Solo con contenedor real: en tests (mock web) no hay ServerContainer. */
    @Bean
    @Profile("!test")
    public ServletServerContainerFactoryBean streamWsContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(512 * 1024);
        container.setMaxBinaryMessageBufferSize(512 * 1024);
        container.setMaxSessionIdleTimeout(300000L);
        return container;
    }
}
