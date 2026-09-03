package com.makerspace.backend.controller;

import com.makerspace.backend.controller.dto.AppRoleDTO;
import com.makerspace.backend.controller.dto.CreateRoleRequest;
import com.makerspace.backend.controller.dto.UpdatePermissionsRequest;
import com.makerspace.backend.model.Permission;
import com.makerspace.backend.repository.PermissionRepository;
import com.makerspace.backend.services.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Admin API for managing roles and their permission sets.
 * All endpoints require the {@code MANAGE_ROLES} permission.
 *
 * URL-level access is enforced by the {@code roleAdminChain} in SecurityConfig;
 * {@code @PreAuthorize} provides method-level double-check.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('" + Permission.MANAGE_ROLES + "')")
public class RoleController {

    private final RoleService roleService;
    private final PermissionRepository permissionRepository;

    public RoleController(RoleService roleService, PermissionRepository permissionRepository) {
        this.roleService = roleService;
        this.permissionRepository = permissionRepository;
    }

    // -------------------------------------------------------------------------
    // Permissions catalog (read-only)
    // -------------------------------------------------------------------------

    /** Lists all available permission codes that can be assigned to roles. */
    @GetMapping("/permissions")
    public Set<String> listPermissions() {
        return permissionRepository.findAllCodes();
    }

    // -------------------------------------------------------------------------
    // Role CRUD
    // -------------------------------------------------------------------------

    @GetMapping("/roles")
    public List<AppRoleDTO> listRoles() {
        return roleService.listAll().stream().map(AppRoleDTO::from).toList();
    }

    @GetMapping("/roles/{code}")
    public AppRoleDTO getRole(@PathVariable String code) {
        return AppRoleDTO.from(roleService.getByCode(code));
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public AppRoleDTO createRole(@Valid @RequestBody CreateRoleRequest req) {
        AppRoleDTO created = AppRoleDTO.from(roleService.createRole(req.code(), req.description()));
        // Apply initial permissions if provided
        if (req.permissions() != null && !req.permissions().isEmpty()) {
            return AppRoleDTO.from(roleService.setPermissions(req.code(), req.permissions()));
        }
        return created;
    }

    /**
     * Replaces the complete permission set for a role.
     * Send an empty set to revoke all permissions.
     */
    @PutMapping("/roles/{code}/permissions")
    public AppRoleDTO setPermissions(@PathVariable String code,
                                     @Valid @RequestBody UpdatePermissionsRequest req) {
        return AppRoleDTO.from(roleService.setPermissions(code, req.permissions()));
    }

    /**
     * Deletes a custom (non-system) role.
     * Returns 409 if the role is built-in or still in use.
     */
    @DeleteMapping("/roles/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable String code) {
        roleService.deleteRole(code);
    }
}
