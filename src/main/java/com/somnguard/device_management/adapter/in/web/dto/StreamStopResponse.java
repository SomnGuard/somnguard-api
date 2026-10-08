package com.somnguard.device_management.adapter.in.web.dto;

import java.util.UUID;

public record StreamStopResponse(UUID sessionId, boolean closed) {}
