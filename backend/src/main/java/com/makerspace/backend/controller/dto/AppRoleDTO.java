package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.AppRole;

import java.util.Set;

public record AppRoleDTO(
        String code,
        String description,
        boolean isSystem,
        Set<String> permissions
) {
    public static AppRoleDTO from(AppRole role) {
        return new AppRoleDTO(
                role.getCode(),
                role.getDescription(),
                role.isSystem(),
                role.getPermissions()
        );
    }
}
