package com.somnguard.parameterization.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record EventCategoryPatchRequest(
        @Size(max = 30) String code,
        @Size(max = 100) String name,
        String description,
        @Min(0) @JsonAlias("sortOrder") Integer sortOrder,
        @JsonAlias("isActive") Boolean isActive
) {}
