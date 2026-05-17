package com.makerspace.backend.services;

import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserStateService {

    public enum State { ACTIVE, DELETED, NOT_FOUND }

    private final UserRepository userRepository;

    public UserStateService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns the current active/deleted/unknown state for the given email.
     * Uses a native query to bypass @SQLRestriction so soft-deleted rows are visible.
     * Cached for 30 seconds to limit DB load on hot request paths.
     */
    @Cacheable("userState")
    @Transactional(readOnly = true)
    public State stateOf(String email) {
        Optional<User> user = userRepository.findByEmailIncludingDeleted(email);
        if (user.isEmpty()) {
            return State.NOT_FOUND;
        }
        return user.get().getDeletedAt() == null ? State.ACTIVE : State.DELETED;
    }

    /**
     * Evicts the cached state for the given email.
     * Must be called whenever a user is soft-deleted or reactivated so the
     * next filter check reflects the new state immediately.
     */
    @CacheEvict(value = "userState", key = "#email")
    public void evict(String email) {}
}