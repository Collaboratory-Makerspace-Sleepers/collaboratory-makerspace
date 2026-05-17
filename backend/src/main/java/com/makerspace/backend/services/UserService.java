package com.makerspace.backend.services;

import com.makerspace.backend.config.security.OAuthProfile;
import com.makerspace.backend.model.Role;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserResolution;
import com.makerspace.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class UserService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserStateService userStateService;

    // -------------------------------------------------------------------------
    // Lookups
    // -------------------------------------------------------------------------

    public User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
    }

    public User findByIdIncludingDeleted(Long id) {
        return userRepository.findByIdIncludingDeleted(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
    }

    public Page<User> findAllActive(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public long countActiveAdmins() {
        return userRepository.countByRole(Role.ADMIN);
    }

    // -------------------------------------------------------------------------
    // OAuth resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves a verified OAuth identity to a user state.
     * Read-only — does not mutate. Caller decides what to do with the result.
     */
    @Transactional(readOnly = true)
    public UserResolution resolve(OAuthProfile profile) {
        return userRepository.findByEmailIncludingDeleted(profile.email())
                .map(user -> user.getDeletedAt() == null
                        ? (UserResolution) new UserResolution.Active(user)
                        : new UserResolution.Deleted(user.getId(), user.getDeletedAt()))
                .orElseGet(() -> new UserResolution.NotFound(profile));
    }

    // -------------------------------------------------------------------------
    // Mutations
    // -------------------------------------------------------------------------

    /**
     * Creates a new user from a verified OAuth identity.
     * Caller is responsible for calling resolve() first and only dispatching here
     * on a NotFound result.
     */
    @Transactional
    public User provision(OAuthProfile profile) {
        User user = new User();
        user.setEmail(profile.email());
        user.setFirstName(profile.firstName());
        user.setLastName(profile.lastName());
        user.setRole(Role.MEMBER);
        return userRepository.save(user);
    }

    @Transactional
    public User updateProfile(Long id, String firstName, String lastName) {
        User user = findById(id);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        return userRepository.save(user);
    }

    @Transactional
    public User updateRole(Long id, Role newRole) {
        User user = findById(id);
        Role oldRole = user.getRole();
        user.setRole(newRole);
        User saved = userRepository.save(user);
        log.info("Role change: user {} (id={}) changed from {} to {}", saved.getEmail(), id, oldRole, newRole);
        return saved;
    }

    /**
     * Soft-deletes the user and immediately evicts their state from the cache.
     * Eviction ensures the JwtAuthFilter sees the deletion on the very next request
     * rather than waiting for the 30-second cache TTL to expire.
     */
    @CacheEvict(value = "userState", key = "#user.email")
    @Transactional
    public void softDelete(User user) {
        userRepository.delete(user);  // @SQLDelete turns this into UPDATE users SET deleted_at = ...
    }

    /**
     * Looks up, validates, and soft-deletes a user by ID.
     * Enforces the last-admin guard: if the target is the only active ADMIN, deletion
     * is rejected. Evicts the user's cache entry so the next authenticated request
     * sees the DELETED state immediately.
     *
     * @param id      DB id of the user to delete
     * @param actorId DB id of the user performing the deletion (for audit logging)
     */
    @Transactional
    public void softDeleteUser(Long id, Long actorId) {
        User user = findById(id);
        if (user.getRole() == Role.ADMIN && countActiveAdmins() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete the last admin");
        }
        log.info("User {} (id={}) soft-deleted by actor id={}", user.getEmail(), id, actorId);
        userRepository.delete(user);
        userStateService.evict(user.getEmail());
    }

    /**
     * Reactivates a soft-deleted user and immediately evicts their cache entry so
     * the next request sees ACTIVE state without waiting for the TTL.
     */
    @Transactional
    public User restore(Long id) {
        User user = findByIdIncludingDeleted(id);
        if (user.getDeletedAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not deleted");
        }
        user.setDeletedAt(null);
        User saved = userRepository.save(user);
        userStateService.evict(saved.getEmail());
        return saved;
    }
}