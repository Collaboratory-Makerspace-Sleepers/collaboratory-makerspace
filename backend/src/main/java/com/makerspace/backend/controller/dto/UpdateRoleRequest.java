package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
        @NotNull Role role
) {}