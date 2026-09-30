package com.somnguard.device_management.adapter.in.web.dto;

import java.util.UUID;

public record DetectionPauseResponse(UUID deviceId, boolean paused) {}
