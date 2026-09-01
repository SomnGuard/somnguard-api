package com.somnguard.security.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignRoleToUserPathRequest(@NotNull @JsonAlias({"roleId", "role_id"}) UUID roleId) {}
