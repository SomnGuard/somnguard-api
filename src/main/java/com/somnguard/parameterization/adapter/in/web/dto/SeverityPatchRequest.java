package com.somnguard.parameterization.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record SeverityPatchRequest(
        @Size(max = 20) String code,
        @Size(max = 50) String name,
        @Min(1) Short priority,
        @JsonAlias("isActive") Boolean isActive
) {}
