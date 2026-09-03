package com.makerspace.backend.services;

import com.makerspace.backend.controller.dto.PreRegisterRequest;
import com.makerspace.backend.controller.dto.PreRegisterResponse;
import com.makerspace.backend.model.AccountStatus;
import com.makerspace.backend.model.AppRole;
import com.makerspace.backend.model.RegistrationInvite;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserProfile;
import com.makerspace.backend.repository.AppRoleRepository;
import com.makerspace.backend.repository.RegistrationInviteRepository;
import com.makerspace.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminRegistrationService {

    private final UserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final RegistrationInviteRepository inviteRepository;
    private final InviteTokenService inviteTokenService;
    private final EmailService emailService;

    public AdminRegistrationService(UserRepository userRepository,
                                    AppRoleRepository roleRepository,
                                    RegistrationInviteRepository inviteRepository,
                                    InviteTokenService inviteTokenService,
                                    EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
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
        user.setRoles(resolveRoles(req.roleCodes()));
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

    /** Resolves a set of role code strings to AppRole entities, validating each exists. */
    private Set<AppRole> resolveRoles(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) return new HashSet<>();
        Set<AppRole> resolved = new HashSet<>(roleRepository.findAllById(roleCodes));
        Set<String> found = resolved.stream().map(AppRole::getCode).collect(Collectors.toSet());
        Set<String> missing = roleCodes.stream().filter(c -> !found.contains(c)).collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown role codes: " + missing);
        }
        return resolved;
    }
}