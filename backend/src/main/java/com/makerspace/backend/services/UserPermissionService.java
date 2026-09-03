package com.makerspace.backend.services;

import com.makerspace.backend.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the effective permission set for a user and caches the result.
 *
 * A user's effective permissions are the union of all permissions belonging to
 * their assigned roles. The result is cached for 30 s (same TTL as userState)
 * so changes made through the admin API propagate within 30 s without
 * reissuing tokens.
 *
 * Eviction rules:
 * <ul>
 *   <li>{@code evict(email)} — call when a user's role assignments change.</li>
 *   <li>{@code evictAll()} — call when a role's permission set changes (affects
 *       every user who holds that role).</li>
 * </ul>
 */
@Service
public class UserPermissionService {

    private final UserRepository userRepository;

    public UserPermissionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Cacheable(value = "userPermissions", key = "#email")
    @Transactional(readOnly = true)
    public Set<String> getEffectivePermissions(String email) {
        return userRepository.findByEmail(email)
                .map(user -> user.getRoles().stream()
                        .flatMap(role -> role.getPermissions().stream())
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    @CacheEvict(value = "userPermissions", key = "#email")
    public void evict(String email) {}

    @CacheEvict(value = "userPermissions", allEntries = true)
    public void evictAll() {}
}
