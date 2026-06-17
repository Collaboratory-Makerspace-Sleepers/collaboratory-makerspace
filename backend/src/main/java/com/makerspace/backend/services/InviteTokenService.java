package com.makerspace.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class InviteTokenService {

    private static final SecureRandom RNG = new SecureRandom();

    @Value("${registration.invite-ttl:P7D}")
    private Duration ttl;

    public record Issued(String rawToken, String tokenHash, Instant expiresAt) {}

    public Issued issue() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Issued(raw, sha256Hex(raw), Instant.now().plus(ttl));
    }

    public String hash(String raw) {
        return sha256Hex(raw);
    }

    private static String sha256Hex(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
