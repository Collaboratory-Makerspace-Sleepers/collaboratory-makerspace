package com.makerspace.backend.services;

import com.makerspace.backend.model.AppRole;
import com.makerspace.backend.repository.AppRoleRepository;
import com.makerspace.backend.repository.PermissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RoleService {

    private final AppRoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionService userPermissionService;

    public RoleService(AppRoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       UserPermissionService userPermissionService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userPermissionService = userPermissionService;
    }

    public List<AppRole> listAll() {
        return roleRepository.findAll();
    }

    public AppRole getByCode(String code) {
        return roleRepository.findById(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found: " + code));
    }

    @Transactional
    public AppRole createRole(String code, String description) {
        if (roleRepository.existsById(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists: " + code);
        }
        AppRole role = new AppRole();
        role.setCode(code.toUpperCase());
        role.setDescription(description);
        role.setSystem(false);
        AppRole saved = roleRepository.save(role);
        log.info("Role created: {}", code);
        return saved;
    }

    @Transactional
    public AppRole updateDescription(String code, String description) {
        AppRole role = getByCode(code);
        role.setDescription(description);
        return roleRepository.save(role);
    }

    /**
     * Replaces the permission set for a role.
     * Evicts all cached user-permission entries because any user holding this
     * role is now affected.
     */
    @Transactional
    public AppRole setPermissions(String code, Set<String> permissionCodes) {
        AppRole role = getByCode(code);

        // Validate that every code exists in the permissions catalog.
        Set<String> known = permissionRepository.findAllCodes();
        Set<String> unknown = permissionCodes.stream()
                .filter(p -> !known.contains(p))
                .collect(Collectors.toSet());
        if (!unknown.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown permission codes: " + unknown);
        }

        role.setPermissions(new HashSet<>(permissionCodes));
        AppRole saved = roleRepository.save(role);

        // Changing a role's permissions affects every user who holds that role.
        userPermissionService.evictAll();
        log.info("Permissions updated for role {}: {}", code, permissionCodes);
        return saved;
    }

    /**
     * Deletes a custom (non-system) role.
     * Blocked if any active user holds the role.
     */
    @Transactional
    public void deleteRole(String code) {
        AppRole role = getByCode(code);
        if (role.isSystem()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Built-in roles cannot be deleted");
        }
        if (roleRepository.isRoleInUse(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Role is still assigned to active users and cannot be deleted");
        }
        roleRepository.delete(role);
        log.info("Role deleted: {}", code);
    }
}
