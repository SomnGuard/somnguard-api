package com.somnguard.security.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyPasswordRequest(
        @NotBlank @Size(min = 1, max = 128) String password
) {}
