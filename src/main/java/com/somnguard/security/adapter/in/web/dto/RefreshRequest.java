package com.somnguard.security.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank @JsonAlias({"refreshToken", "refresh_token"}) String refreshToken) {}
