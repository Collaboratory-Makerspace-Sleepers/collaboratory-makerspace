package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * Request body for {@code PATCH /api/v1/users/{id}/role}.
 * Contains the set of role codes (e.g. "STAFF", "INSTRUCTOR") to assign to the user.
 */
public record UpdateRoleRequest(
        @NotNull Set<String> roleCodes
) {}
