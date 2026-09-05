package com.somnguard.parameterization.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SoundPatternPatchRequest(
        @Size(max = 30) String code,
        String description,
        @Min(1) @JsonAlias("frequencyHz") Integer frequencyHz,
        @Min(1) @JsonAlias("durationMs") Integer durationMs,
        @Min(0) Short repetitions,
        @Pattern(regexp = "beep|continuous|intermittent|escalating") @JsonAlias("patternType") String patternType,
        @Min(1) @JsonAlias("intervalMs") Integer intervalMs,
        @JsonAlias("isActive") Boolean isActive
) {}
