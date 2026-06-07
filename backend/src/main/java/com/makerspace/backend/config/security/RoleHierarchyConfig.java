package com.makerspace.backend.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

@Configuration
public class RoleHierarchyConfig {

    /**
     * Authority DAG: ADMIN implies STAFF and INSTRUCTOR.
     * STAFF and INSTRUCTOR are siblings — neither implies the other.
     * <p>
     * Auto-detected by Spring Security 6.3 for both web and method security.
     */
    @Bean
    public static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("STAFF", "INSTRUCTOR")
                .build();
    }
}