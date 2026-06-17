package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.AccountStatus;
import com.makerspace.backend.model.Role;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserProfile;

import java.util.Set;

public record PreRegisterResponse(
        Long id,
        String email,
        String fullName,
        AccountStatus status,
        Set<Role> authorityRoles,
        boolean inviteSent
) {
    public static PreRegisterResponse from(User user, boolean inviteSent) {
        UserProfile profile = user.getProfile();
        String fullName = profile != null
                ? ((profile.getFirstName() != null ? profile.getFirstName() : "")
                   + (profile.getLastName() != null ? " " + profile.getLastName() : "")).strip()
                : null;
        return new PreRegisterResponse(
                user.getId(),
                user.getEmail(),
                fullName,
                user.getAccountStatus(),
                user.getRoles(),
                inviteSent
        );
    }
}
