package com.somnguard.device_management.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.UUID;

public record AssignDeviceRequest(
        @JsonAlias({"userId", "user_id"})
        UUID userId
) {}
