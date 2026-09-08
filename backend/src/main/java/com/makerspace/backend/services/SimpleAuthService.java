package com.makerspace.backend.services;

import com.makerspace.backend.model.AccountStatus;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserProfile;
import com.makerspace.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Simple email/password authentication used by the sign-up and sign-in forms.
 * Kept deliberately decoupled from the OAuth2/Auth0 login flow.
 */
@Service
public class SimpleAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public SimpleAuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String firstName, String lastName, String email, String password) {
        String normalizedEmail = normalize(email);

        if (userRepository.existsByEmailIncludingDeleted(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        UserProfile profile = new UserProfile();
        profile.setFirstName(firstName);
        profile.setLastName(lastName);

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setProfile(profile);

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User login(String email, String password) {
        String normalizedEmail = normalize(email);

        User user = userRepository.findByEmailIncludingDeleted(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (user.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account_closed");
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        return user;
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}