package com.makerspace.backend.model;

import com.makerspace.backend.config.security.OAuthProfile;

import java.time.LocalDateTime;

public sealed interface UserResolution {
    record Active(User user) implements UserResolution {}
    /** Pre-registered account whose email matches — needs auto-claim or explicit claim. */
    record Pending(User user) implements UserResolution {}
    record NotFound(OAuthProfile profile) implements UserResolution {}
    record Deleted(Long userId, LocalDateTime deletedOn) implements UserResolution {}
}
