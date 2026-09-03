package com.makerspace.backend.model;

/**
 * Well-known permission codes.
 *
 * Each constant maps to a row in the {@code permissions} table and is the
 * string stored in {@code role_permissions.permission}. Security filter
 * chains and {@code @PreAuthorize} expressions reference these constants
 * rather than the magic strings directly.
 *
 * New capabilities must be added here AND seeded in a Flyway migration.
 */
public final class Permission {

    // User management
    public static final String MANAGE_USERS  = "MANAGE_USERS";
    public static final String MANAGE_ROLES  = "MANAGE_ROLES";

    // Equipment
    public static final String MANAGE_EQUIPMENT = "MANAGE_EQUIPMENT";

    // Reservations
    public static final String VIEW_ALL_RESERVATIONS = "VIEW_ALL_RESERVATIONS";
    public static final String MANAGE_RESERVATIONS   = "MANAGE_RESERVATIONS";

    // Registration / onboarding
    public static final String REGISTER_USERS = "REGISTER_USERS";

    private Permission() {}
}
