package com.somnguard.security.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAdminUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) @JsonAlias({"first_name", "firstName"}) String firstName,
        @NotBlank @Size(max = 100) @JsonAlias({"last_name", "lastName"}) String lastName,
        @Size(max = 30) String phone
) {}
