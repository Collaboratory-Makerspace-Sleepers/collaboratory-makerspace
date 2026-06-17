package com.makerspace.backend.services;

import com.makerspace.backend.controller.dto.PreRegisterRequest;
import com.makerspace.backend.controller.dto.PreRegisterResponse;
import com.makerspace.backend.model.AccountStatus;
import com.makerspace.backend.model.RegistrationInvite;
import com.makerspace.backend.model.Role;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserProfile;
import com.makerspace.backend.repository.RegistrationInviteRepository;
import com.makerspace.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminRegistrationService {

    private final UserRepository userRepository;
    private final RegistrationInviteRepository inviteRepository;
    private final InviteTokenService inviteTokenService;
    private final EmailService emailService;

    public AdminRegistrationService(UserRepository userRepository,
                                    RegistrationInviteRepository inviteRepository,
                                    InviteTokenService inviteTokenService,
                                    EmailService emailService) {
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.inviteTokenService = inviteTokenService;
        this.emailService = emailService;
    }

    @Transactional
    public PreRegisterResponse preRegister(PreRegisterRequest req, Long adminUserId) {
        String email = req.email().toLowerCase().strip();

        if (userRepository.existsByEmailIncludingDeleted(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with this email already exists");
        }

        UserProfile profile = new UserProfile();
        String[] parts = req.fullName().strip().split("\\s+", 2);
        profile.setFirstName(parts[0]);
        profile.setLastName(parts.length > 1 ? parts[1] : null);

        User user = new User();
        user.setEmail(email);
        user.setProfile(profile);
        user.setAccountStatus(AccountStatus.PRE_REGISTERED);
        user.setAuth0Subject(null);
        user.setRoles(requireAuthorityRoles(req.authorityRoles()));
        userRepository.save(user);

        boolean invited = false;
        if (req.sendInvite()) {
            issueAndSendInvite(user, adminUserId);
            invited = true;
        }

        log.info("Pre-registered user {} (id={}) by admin id={}, invite={}", email, user.getId(), adminUserId, invited);
        return PreRegisterResponse.from(user, invited);
    }

    @Transactional
    public void resendInvite(Long userId, Long adminUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getAccountStatus() != AccountStatus.PRE_REGISTERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is already active");
        }
        issueAndSendInvite(user, adminUserId);
        log.info("Resent invite for user {} (id={}) by admin id={}", user.getEmail(), userId, adminUserId);
    }

    private void issueAndSendInvite(User user, Long adminUserId) {
        InviteTokenService.Issued issued = inviteTokenService.issue();

        RegistrationInvite invite = new RegistrationInvite();
        invite.setUserId(user.getId());
        invite.setTokenHash(issued.tokenHash());
        invite.setIntendedEmail(user.getEmail());
        invite.setExpiresAt(issued.expiresAt());
        invite.setCreatedBy(adminUserId);
        inviteRepository.save(invite);

        String fullName = user.getProfile() != null
                ? (user.getProfile().getFirstName() + " " + user.getProfile().getLastName()).strip()
                : user.getEmail();
        emailService.sendRegistrationInvite(user.getEmail(), fullName, issued.rawToken());
    }

    private Set<Role> requireAuthorityRoles(Set<Role> roles) {
        if (roles == null) return Set.of();
        Set<Role> invalid = roles.stream().filter(r -> !r.isAuthorityRole()).collect(Collectors.toSet());
        if (!invalid.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Non-authority roles cannot be granted at registration: " + invalid);
        }
        return roles;
    }
}