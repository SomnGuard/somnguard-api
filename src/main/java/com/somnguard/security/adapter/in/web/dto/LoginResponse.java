package com.somnguard.security.adapter.in.web.dto;

public record LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {}
