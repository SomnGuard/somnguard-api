package com.somnguard.security.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignUserRoleRequest(@NotNull UUID userId, @NotNull UUID roleId) {}
