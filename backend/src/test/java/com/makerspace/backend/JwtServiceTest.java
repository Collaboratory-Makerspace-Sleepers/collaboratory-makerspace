package com.makerspace.backend;

import com.makerspace.backend.model.Role;
import com.makerspace.backend.model.User;
import com.makerspace.backend.services.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    // Must be >= 32 bytes for HS256
    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3Rpbmctb25seQ==";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", 86400000L);
    }

    private User makeUser(Long id, String email, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRole(role);
        return user;
    }

    @Test
    void generatedTokenIsValid() {
        User user = makeUser(1L, "member@test.com", Role.MEMBER);
        String token = jwtService.generateToken(user);
        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    void parsedTokenHasCorrectClaims() {
        User user = makeUser(42L, "staff@test.com", Role.STAFF);
        String token = jwtService.generateToken(user);

        Claims claims = jwtService.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("staff@test.com");
        assertThat(claims.get("role", String.class)).isEqualTo("STAFF");
    }

    @Test
    void tamperedTokenIsInvalid() {
        User user = makeUser(1L, "user@test.com", Role.MEMBER);
        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void randomStringIsInvalid() {
        assertThat(jwtService.isValid("not.a.token")).isFalse();
    }
}
