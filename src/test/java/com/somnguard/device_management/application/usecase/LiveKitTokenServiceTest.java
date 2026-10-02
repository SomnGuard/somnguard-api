package com.somnguard.device_management.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jwt.SignedJWT;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LiveKitTokenServiceTest {

    final LiveKitTokenService service =
            new LiveKitTokenService(true, "wss://live.local", "devkey", "dGVzdHNlY3JldGtleXRlc3RzZWNyZXRrZXl0ZXN0c2VjcmV0a2V5", 60);

    @Test
    void disabledWithoutConfig() {
        assertTrue(!new LiveKitTokenService(false, "", "", "", 60).isEnabled());
        assertTrue(!new LiveKitTokenService(true, "", "k", "s", 60).isEnabled());
        assertTrue(service.isEnabled());
    }

    @Test
    void viewerTokenGrantsSubscribeOnly() throws Exception {
        UUID device = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        String token = service.viewerToken(device, viewer);
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);
        var claims = SignedJWT.parse(token).getJWTClaimsSet();
        assertEquals("devkey", claims.getIssuer());
        assertTrue(claims.getSubject().startsWith("viewer-" + viewer + "-"),
                "identidad única por emisión: " + claims.getSubject());
        @SuppressWarnings("unchecked")
        var video = (java.util.Map<String, Object>) claims.getClaim("video");
        assertEquals("somnguard-" + device.toString().substring(0, 8), video.get("room"));
        assertEquals(Boolean.TRUE, video.get("roomJoin"));
        assertEquals(Boolean.TRUE, video.get("canSubscribe"));
        assertEquals(Boolean.FALSE, video.get("canPublish"));
    }

    @Test
    void publisherTokenGrantsPublishOnly() throws Exception {
        UUID device = UUID.randomUUID();
        String token = service.publisherToken(device);
        var claims = SignedJWT.parse(token).getJWTClaimsSet();
        @SuppressWarnings("unchecked")
        var video = (java.util.Map<String, Object>) claims.getClaim("video");
        assertEquals(Boolean.TRUE, video.get("canPublish"));
        assertEquals(Boolean.FALSE, video.get("canSubscribe"));
    }
}
