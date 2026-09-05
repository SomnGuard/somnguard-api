package com.somnguard.parameterization.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record EventTypePatchRequest(
        @Size(max = 30) String code,
        @Size(max = 100) String name,
        @JsonAlias("eventCategoryId") UUID eventCategoryId,
        @JsonAlias("defaultSeverityId") UUID defaultSeverityId,
        @JsonAlias("defaultSoundPatternId") UUID defaultSoundPatternId,
        @JsonAlias("thresholdConfig") Map<String, Object> thresholdConfig,
        String status,
        @JsonAlias("statusCategory") String statusCategory,
        @JsonAlias("isActive") Boolean isActive
) {}
