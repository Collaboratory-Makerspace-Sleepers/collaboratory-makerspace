package com.makerspace.backend.services;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.controller.dto.UserDTO;
import com.makerspace.backend.model.AccountStatus;
import com.makerspace.backend.model.RegistrationInvite;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.RegistrationInviteRepository;
import com.makerspace.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Slf4j
@Service
public class AccountClaimService {

    private final UserRepository userRepository;
    private final RegistrationInviteRepository inviteRepository;
    private final InviteTokenService inviteTokenService;
    private final UserStateService userStateService;

    public AccountClaimService(UserRepository userRepository,
                               RegistrationInviteRepository inviteRepository,
                               InviteTokenService inviteTokenService,
                               UserStateService userStateService) {
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.inviteTokenService = inviteTokenService;
        this.userStateService = userStateService;
    }

    /**
     * Consumes an invite token and links the authenticated identity to the pre-registered account.
     * The verified email from the JWT must match the email on the pre-registered record —
     * possession of the link alone is not sufficient.
     *
     * @param principal the authenticated caller (ROLE_PENDING, auth0Subject from JWT)
     * @param rawToken  the single-use token from the invite link
     * @return the activated user as a DTO (frontend should re-authenticate to get a full-role JWT)
     */
    @Transactional
    public UserDTO claim(UserPrincipal principal, String rawToken) {
        RegistrationInvite invite = inviteRepository
                .findByTokenHash(inviteTokenService.hash(rawToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid invite token"));

        if (!invite.isUsable(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This invite has expired or has already been used");
        }

        // Pessimistic lock so concurrent claims on the same record serialize.
        User user = userRepository.findByIdForUpdate(invite.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This account is closed — please contact staff");
        }

        // Idempotent: same identity re-submitting the link returns success.
        if (user.getAccountStatus() == AccountStatus.ACTIVE
                && principal.auth0Subject() != null
                && principal.auth0Subject().equals(user.getAuth0Subject())) {
            invite.consume();
            return UserDTO.from(user);
        }

        // Conflict: already linked to a different identity.
        if (user.getAuth0Subject() != null
                && !user.getAuth0Subject().equals(principal.auth0Subject())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This registration is already linked to another account");
        }

        // THE GATE — verified email from the JWT must match the pre-registered email.
        if (!normalize(principal.email()).equals(normalize(user.getEmail()))) {
            log.warn("Email mismatch on claim: principal={}, record={}, invite={}",
                    principal.email(), user.getEmail(), invite.getId());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You signed in as " + principal.email()
                    + " but were invited as " + user.getEmail()
                    + ". Please contact staff to correct the record.");
        }

        user.setAuth0Subject(principal.auth0Subject());
        user.setAccountStatus(AccountStatus.ACTIVE);
        invite.consume();
        userStateService.evict(user.getEmail());

        log.info("Account claimed: user {} (id={}) linked to auth0Subject={}",
                user.getEmail(), user.getId(), principal.auth0Subject());

        return UserDTO.from(user);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.toLowerCase().strip();
    }
}
