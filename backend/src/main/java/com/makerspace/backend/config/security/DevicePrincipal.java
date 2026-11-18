package com.makerspace.backend.config.security;

import java.util.UUID;

public record DevicePrincipal(UUID id, String keyHash) {}
