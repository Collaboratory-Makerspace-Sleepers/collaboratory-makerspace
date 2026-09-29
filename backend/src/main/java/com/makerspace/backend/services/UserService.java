package com.makerspace.backend.services;

import com.makerspace.backend.config.security.OAuthProfile;
import com.makerspace.backend.model.AccountStatus;
import com.makerspace.backend.model.AppRole;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserProfile;
import com.makerspace.backend.model.UserResolution;
import com.makerspace.backend.repository.AppRoleRepository;
import com.makerspace.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Slf4j
@Service
public class UserService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserStateService userStateService;

    @Autowired
    AppRoleRepository roleRepository;

    /**
     * Role assigned to accounts created by self-provisioning (first-time OAuth or
     * OTP sign-in). Empty by default, which means a new account starts with no
     * permissions and must be promoted through the admin API or the
     * {@code /claim} invite flow.
     *
     * Setting this to a privileged code such as {@code ADMIN} grants that role to
     * every account that signs in. That is a development convenience only — it
     * makes the instance open to anyone who completes an OAuth handshake, so it
     * must stay unset in any deployed environment.
     */
    @Value("${app.registration.default-role:}")
    private String defaultRole;

    // -------------------------------------------------------------------------
    // Lookups
    // -------------------------------------------------------------------------

    public User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    boolean deleted = userRepository.findByEmailIncludingDeleted(email).isPresent();
                    return new ResponseStatusException(
                            deleted ? HttpStatus.GONE : HttpStatus.NOT_FOUND,
                            deleted ? "User account has been deleted" : "User not found");
                });
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
        return userRepository.countByRoleCode("ADMIN");
    }

    // -------------------------------------------------------------------------
    // OAuth resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves a verified OAuth identity to a user state.
     * Read-only — does not mutate. Caller decides what to do with the result.
     *
     * Resolution order:
     * 1. Exact auth0Subject match — returning active/deleted user.
     * 2. Email match — pre-registered (Pending) or active user waiting to be linked.
     * 3. No match — NotFound (caller provisions a new GUEST or rejects).
     */
    @Transactional(readOnly = true)
    public UserResolution resolve(OAuthProfile profile) {
        // 1. Exact subject match (fast path for returning users).
        return userRepository.findByAuth0Subject(profile.subject())
                .map(user -> user.getDeletedAt() != null
                        ? (UserResolution) new UserResolution.Deleted(user.getId(), user.getDeletedAt())
                        : new UserResolution.Active(user))
                // 2. Email match — handle pre-registered and already-active accounts.
                .orElseGet(() -> userRepository.findByEmailIncludingDeleted(profile.email())
                        .map(user -> {
                            if (user.getDeletedAt() != null)
                                return (UserResolution) new UserResolution.Deleted(user.getId(), user.getDeletedAt());
                            if (user.getAccountStatus() == AccountStatus.PRE_REGISTERED)
                                return new UserResolution.Pending(user);
                            return new UserResolution.Active(user);
                        })
                        .orElseGet(() -> new UserResolution.NotFound(profile)));
    }

    // -------------------------------------------------------------------------
    // Mutations
    // -------------------------------------------------------------------------

    /**
     * Creates a new user from a verified OAuth identity.
     * Caller is responsible for calling resolve() first and only dispatching here
     * on a NotFound result. The account is granted the configured
     * {@code app.registration.default-role}, if any.
     */
    @Transactional
    public User provision(OAuthProfile oAuthProfile) {
        UserProfile profile = new UserProfile();
        profile.setFirstName(oAuthProfile.firstName());
        profile.setLastName(oAuthProfile.lastName());

        User user = new User();
        user.setEmail(oAuthProfile.email());
        user.setAuth0Subject(oAuthProfile.subject());
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setProfile(profile);
        applyDefaultRole(user);
        return userRepository.save(user);
    }

    /**
     * Creates an active account from a bare email address (no OAuth subject).
     * Used by the email one-time-passcode flow, where the address itself is the
     * proof of identity. Caller must confirm the email can receive codes first.
     */
    @Transactional
    public User provisionByEmail(String email) {
        int at = email.indexOf('@');
        String localPart = at > 0 ? email.substring(0, at) : email;

        UserProfile profile = new UserProfile();
        profile.setFirstName(localPart);

        User user = new User();
        user.setEmail(email);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setProfile(profile);
        applyDefaultRole(user);
        return userRepository.save(user);
    }

    /**
     * Grants the configured default role to a freshly created account.
     * No-op when the property is unset or the code does not match a known role —
     * a bad value must not prevent someone from signing up.
     */
    private void applyDefaultRole(User user) {
        if (defaultRole == null || defaultRole.isBlank()) return;

        roleRepository.findById(defaultRole.trim().toUpperCase()).ifPresentOrElse(role -> {
            user.getRoles().add(role);
            log.info("Granted default role {} to newly provisioned account", role.getCode());
        }, () -> log.warn(
                "app.registration.default-role='{}' does not match a known role code; "
                        + "account created with no roles", defaultRole));
    }

    /**
     * Creates an unverified account for public self-registration.
     *
     * The account lands in {@code PRE_REGISTERED}, which carries no usable
     * authority: it cannot authenticate until the caller proves control of the
     * address through the OTP flow, at which point
     * {@link UserStateService#autoClaimByEmail} flips it to {@code ACTIVE}.
     * This is what keeps registration from being an account-takeover primitive —
     * anyone can claim an address, but only its real owner can finish signing up.
     *
     * @throws ResponseStatusException 409 when the address is already registered
     */
    @Transactional
    public User preRegister(String email, String firstName, String lastName) {
        String normalized = email.trim().toLowerCase();

        if (userRepository.findByEmailIncludingDeleted(normalized).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        UserProfile profile = new UserProfile();
        profile.setFirstName(firstName);
        profile.setLastName(lastName);

        User user = new User();
        user.setEmail(normalized);
        user.setAccountStatus(AccountStatus.PRE_REGISTERED);
        user.setProfile(profile);
        applyDefaultRole(user);
        return userRepository.save(user);
    }

    @Transactional
    public User updateProfile(Long id, String firstName, String lastName) {
        User user = findById(id);
        UserProfile profile = user.getProfile();
        if (profile == null) {
            profile = new UserProfile();
            user.setProfile(profile);
        }
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        return userRepository.save(user);
    }

    @Transactional
    public User updateRoles(Long id, Set<AppRole> newRoles) {
        User user = findById(id);
        Set<AppRole> oldRoles = user.getRoles();

        boolean wasAdmin = user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getCode()));
        boolean staysAdmin = newRoles.stream().anyMatch(r -> "ADMIN".equals(r.getCode()));

        if (wasAdmin && !staysAdmin && countActiveAdmins() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot demote the last admin");
        }

        user.setRoles(newRoles);
        User saved = userRepository.save(user);
        log.info("Role change: user {} (id={}) changed from {} to {}",
                saved.getEmail(), id,
                oldRoles.stream().map(AppRole::getCode).toList(),
                newRoles.stream().map(AppRole::getCode).toList());
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
        boolean isAdmin = user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getCode()));
        if (isAdmin && countActiveAdmins() <= 1) {
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
