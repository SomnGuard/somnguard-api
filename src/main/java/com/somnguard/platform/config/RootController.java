package com.somnguard.platform.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of(
            "service", "somnguard-api",
            "version", "0.0.1-SNAPSHOT",
            "docs", "/swagger-ui.html",
            "health", "/actuator/health",
            "api-docs", "/v3/api-docs"
        );
    }
}