package com.somnguard.security.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "code must be 6 digits") @JsonAlias({"token", "code"}) String token,
        @NotBlank @Size(min = 8, max = 72) @JsonAlias({"new_password", "newPassword"}) String newPassword
) {}
