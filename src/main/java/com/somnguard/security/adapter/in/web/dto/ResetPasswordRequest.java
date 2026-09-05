package com.somnguard.security.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @JsonAlias({"token", "token"}) String token,
        @NotBlank @Size(min = 8, max = 72) @JsonAlias({"new_password", "newPassword"}) String newPassword
) {}
