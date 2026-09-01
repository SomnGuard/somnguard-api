package com.somnguard.security.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        @Size(max = 255)
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be 8-72 characters")
        String password,

        @NotBlank(message = "firstName is required")
        @Size(max = 100)
        @JsonAlias({"first_name", "firstName"})
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 100)
        @JsonAlias({"last_name", "lastName"})
        String lastName,

        @Size(max = 30)
        String phone
) {}
