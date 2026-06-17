package com.makerspace.backend.services;

import com.makerspace.backend.model.AccountStatus;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class UserStateService {

    public enum State { ACTIVE, PENDING, DELETED, NOT_FOUND }

    private final UserRepository userRepository;

    public UserStateService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns the current state for the given email.
     * Uses a native query to bypass @SQLRestriction so soft-deleted rows are visible.
     * Cached for 30 seconds to limit DB load on hot request paths.
     */
    @Cacheable("userState")
    @Transactional(readOnly = true)
    public State stateOf(String email) {
        Optional<User> user = userRepository.findByEmailIncludingDeleted(email);
        if (user.isEmpty()) return State.NOT_FOUND;
        User u = user.get();
        if (u.getDeletedAt() != null) return State.DELETED;
        return u.getAccountStatus() == AccountStatus.PRE_REGISTERED ? State.PENDING : State.ACTIVE;
    }

    /**
     * Links the Auth0 subject to the pre-registered account identified by userId and
     * flips its status to ACTIVE. Uses a pessimistic write lock to serialize concurrent
     * claims. The cache is evicted so the next filter check sees ACTIVE immediately.
     */
    @CacheEvict(value = "userState", key = "#result.email")
    @Transactional
    public User autoClaimByEmail(Long userId, String auth0Subject) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setAuth0Subject(auth0Subject);
        user.setAccountStatus(AccountStatus.ACTIVE);
        return userRepository.save(user);
    }

    /**
     * Evicts the cached state for the given email.
     * Must be called whenever a user is soft-deleted or reactivated so the
     * next filter check reflects the new state immediately.
     */
    @CacheEvict(value = "userState", key = "#email")
    public void evict(String email) {}
}