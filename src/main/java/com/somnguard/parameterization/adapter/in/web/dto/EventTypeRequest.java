package com.somnguard.parameterization.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record EventTypeRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull @JsonAlias("eventCategoryId") UUID eventCategoryId,
        @NotNull @JsonAlias("defaultSeverityId") UUID defaultSeverityId,
        @NotNull @JsonAlias("defaultSoundPatternId") UUID defaultSoundPatternId,
        @JsonAlias("thresholdConfig") Map<String, Object> thresholdConfig,
        String status,
        @JsonAlias("statusCategory") String statusCategory
) {}
