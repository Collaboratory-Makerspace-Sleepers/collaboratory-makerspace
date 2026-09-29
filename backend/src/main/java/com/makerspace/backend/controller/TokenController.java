package com.makerspace.backend.controller;

import com.makerspace.backend.services.JwtService;
import com.makerspace.backend.services.UserStateService;
import com.makerspace.backend.services.UserStateService.State;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Map;

@Controller
@RequestMapping("/api/v1/auth")
public class TokenController {

    @Autowired
    JwtService jwtService;

    @Autowired
    UserStateService userStateService;

    @GetMapping("/token")
    public ResponseEntity<Map<String, String>> exchangeToken(HttpServletRequest request) {
        // getCookies() returns null when the request carries none at all, so this
        // has to tolerate absence rather than NPE into a 500 — the client treats
        // a non-2xx here as "not signed in" and bounces to /login.
        Cookie[] cookies = request.getCookies();
        String token = cookies == null ? null : Arrays.stream(cookies)
                .filter(c -> "access_token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        if (!jwtService.isValid(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Claims claims = jwtService.parseToken(token);
        String email = claims.get("email", String.class);

        State state = userStateService.stateOf(email);

        if (state == State.DELETED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account_closed");
        }
        if (state == State.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return ResponseEntity.ok(Map.of("access_token", token));
    }
}