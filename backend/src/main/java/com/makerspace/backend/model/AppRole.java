package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * A named role stored in the {@code roles} table.
 *
 * Each role owns an explicit set of permission codes (strings from the
 * {@code role_permissions} table). There is no role hierarchy — a role grants
 * exactly and only the permissions assigned to it. If two roles should share
 * permissions, assign those permissions to both rows; the ADMIN does not
 * "inherit" STAFF permissions implicitly.
 *
 * {@code isSystem} marks built-in roles (ADMIN, STAFF, etc.) that cannot be
 * deleted through the admin API, though their permission sets can still be
 * modified.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "roles")
public class AppRole {

    /** Machine-readable identifier, e.g. "ADMIN", "STAFF", "CUSTOM_ROLE". */
    @Id
    @Column(name = "code", length = 30, nullable = false, updatable = false)
    private String code;

    @Column(name = "description", nullable = false)
    private String description;

    /**
     * Built-in roles cannot be deleted; their permissions can still be changed.
     * Set by the V5 migration and by {@code RoleService.createRole} (always false
     * for admin-created roles).
     */
    @Column(name = "is_system", nullable = false)
    private boolean isSystem = false;

    /**
     * The set of permission codes this role grants.
     * Backed by the {@code role_permissions} join table.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_code")
    )
    @Column(name = "permission", length = 100)
    private Set<String> permissions = new HashSet<>();
}
