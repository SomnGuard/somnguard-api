package com.somnguard.security.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyResetCodeRequest(
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "code must be 6 digits") @JsonAlias({"token", "code"}) String code
) {}
