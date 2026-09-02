package com.somnguard.security.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateMeRequest(
        @Size(max = 100) @JsonAlias({"first_name", "firstName"}) String firstName,
        @Size(max = 100) @JsonAlias({"last_name", "lastName"}) String lastName,
        @Email @Size(max = 255) String email,
        @Size(max = 30) String phone
) {}
