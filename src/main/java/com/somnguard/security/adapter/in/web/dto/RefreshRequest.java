package com.somnguard.security.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record RefreshRequest(@JsonAlias({"refreshToken", "refresh_token"}) String refreshToken) {}
