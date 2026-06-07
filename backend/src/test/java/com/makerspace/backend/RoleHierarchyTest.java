package com.makerspace.backend;

import com.makerspace.backend.config.security.RoleHierarchyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the authority-role DAG:
 *   ADMIN → STAFF
 *   ADMIN → INSTRUCTOR
 *   STAFF and INSTRUCTOR are siblings (neither implies the other)
 */
class RoleHierarchyTest {

    private final RoleHierarchy hierarchy = RoleHierarchyConfig.roleHierarchy();

    private Set<String> reachableFrom(String role) {
        Collection<?> reachable = hierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return reachable.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void admin_impliesStaff() {
        assertThat(reachableFrom("ADMIN")).contains("ROLE_STAFF");
    }

    @Test
    void admin_impliesInstructor() {
        assertThat(reachableFrom("ADMIN")).contains("ROLE_INSTRUCTOR");
    }

    @Test
    void staff_doesNotImplyInstructor() {
        assertThat(reachableFrom("STAFF")).doesNotContain("ROLE_INSTRUCTOR");
    }

    @Test
    void instructor_doesNotImplyStaff() {
        assertThat(reachableFrom("INSTRUCTOR")).doesNotContain("ROLE_STAFF");
    }

    @Test
    void staff_doesNotImplyAdmin() {
        assertThat(reachableFrom("STAFF")).doesNotContain("ROLE_ADMIN");
    }
}