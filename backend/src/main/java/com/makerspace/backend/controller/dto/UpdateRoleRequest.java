package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.Role;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateRoleRequest(
        @NotNull Set<Role> roles
) {}