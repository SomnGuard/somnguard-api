package com.somnguard.parameterization.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record MediaTypePatchRequest(
        @Size(max = 20) String code,
        @Size(max = 50) String name,
        @Size(max = 50) @JsonAlias("mimeType") String mimeType,
        @Min(1) @JsonAlias("maxSizeMb") Integer maxSizeMb,
        @JsonAlias("isActive") Boolean isActive
) {}
