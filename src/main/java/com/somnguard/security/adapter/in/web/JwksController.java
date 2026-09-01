package com.somnguard.security.adapter.in.web;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.somnguard.security.application.service.JwtService;

@RestController
public class JwksController {

    private final JwtService jwtService;

    public JwksController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        RSAKey rsaKey = new RSAKey.Builder(jwtService.getPublicKey()).keyID("somnguard-key-1").build();
        JWKSet set = new JWKSet(rsaKey);
        return set.toJSONObject();
    }
}
