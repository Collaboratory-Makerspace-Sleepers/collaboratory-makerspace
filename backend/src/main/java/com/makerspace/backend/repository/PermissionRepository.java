package com.makerspace.backend.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only access to the permissions catalog.
 *
 * Permissions are seeded by Flyway and updated by future migrations; this
 * repository exposes them for validation in {@code RoleService}. Uses
 * {@link JdbcClient} (Spring 6.1+) to avoid a full JPA entity for a
 * trivially simple lookup table.
 */
@Repository
public class PermissionRepository {

    private final JdbcClient jdbc;

    public PermissionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> findAllCodes() {
        return jdbc.sql("SELECT code FROM permissions")
                .query(String.class)
                .set()
                .stream()
                .collect(Collectors.toSet());
    }
}
