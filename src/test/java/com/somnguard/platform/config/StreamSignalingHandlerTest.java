package com.somnguard.platform.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.somnguard.device_management.application.usecase.StreamSessionService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class StreamSignalingHandlerTest {

    private StreamSessionService streamSessionService;
    private StreamSignalingHandler handler;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        streamSessionService = mock(StreamSessionService.class);
        handler = new StreamSignalingHandler(streamSessionService);
        session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
    }

    @Test
    void handleTextMessage_invalidJson_sendsError() throws Exception {
        handler.handleTextMessage(session, new TextMessage("not-json"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("JSON inválido"));
    }

    @Test
    void handleTextMessage_unknownType_sendsError() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"unknown-hack\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("type desconocido"));
    }

    @Test
    void handleTextMessage_missingSessionId_sendsError() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"subscribe\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("session_id requerido"));
    }

    @Test
    void handleTextMessage_inactiveOrExpiredSession_sendsError() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(streamSessionService.isValidActiveSession(sessionId)).thenReturn(false);

        String msg = "{\"type\":\"subscribe\",\"session_id\":\"" + sessionId + "\"}";
        handler.handleTextMessage(session, new TextMessage(msg));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("Sesión de streaming no válida o expirada"));
    }

    @Test
    void handleTextMessage_validActiveSession_relaysMessage() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(streamSessionService.isValidActiveSession(sessionId)).thenReturn(true);

        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.isOpen()).thenReturn(true);

        String join1 = "{\"type\":\"subscribe\",\"session_id\":\"" + sessionId + "\"}";
        handler.handleTextMessage(session, new TextMessage(join1));
        handler.handleTextMessage(session2, new TextMessage(join1));

        String offerMsg = "{\"type\":\"offer\",\"session_id\":\"" + sessionId + "\",\"sdp\":\"v=0...\"}";
        handler.handleTextMessage(session, new TextMessage(offerMsg));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session2).sendMessage(captor.capture());
        assertEquals(offerMsg, captor.getValue().getPayload());
    }
}
