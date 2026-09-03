package com.somnguard.parameterization.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SoundPatternRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank String description,
        @NotNull @Min(1) @JsonAlias("frequencyHz") Integer frequencyHz,
        @NotNull @Min(1) @JsonAlias("durationMs") Integer durationMs,
        @Min(0) Short repetitions,
        @Pattern(regexp = "beep|continuous|intermittent|escalating") @JsonAlias("patternType") String patternType,
        @Min(1) @JsonAlias("intervalMs") Integer intervalMs
) {}
