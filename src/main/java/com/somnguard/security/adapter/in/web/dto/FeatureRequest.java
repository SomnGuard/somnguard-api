package com.somnguard.security.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record FeatureRequest(
        @NotNull @JsonAlias({"moduleId", "module_id"}) UUID moduleId,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name,
        String description
) {}
