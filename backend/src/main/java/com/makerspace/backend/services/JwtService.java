package com.makerspace.backend.services;

import com.makerspace.backend.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long expiration;

    private SecretKey getSignInKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates an internal JWT for the given user.
     *
     * @param user           the user to issue the token for
     * @param auth0Subject   the Auth0 subject claim from the OidcUser (may differ from
     *                       user.getAuth0Subject() for PRE_REGISTERED accounts not yet linked in DB)
     */
    public String generateToken(User user, String auth0Subject) {
        List<String> roleNames = user.getRoles().stream()
                .map(r -> r.getCode())
                .toList();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roleNames)
                .claim("auth0Subject", auth0Subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false; // TODO: log the error (LIKELY IN THE CONTROLLER)
        }
    }
}