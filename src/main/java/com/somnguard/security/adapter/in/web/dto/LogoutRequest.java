package com.somnguard.security.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record LogoutRequest(@JsonAlias({"refreshToken", "refresh_token"}) String refreshToken) {}
