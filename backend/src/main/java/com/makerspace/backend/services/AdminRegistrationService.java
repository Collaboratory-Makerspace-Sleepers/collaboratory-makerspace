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
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final Set<String> NON_ELEVATED = Set.of("MEMBER", "GUEST", "STUDENT", "RENTEE");

    @Autowired private UserRepository userRepository;
    @Autowired private AppRoleRepository roleRepository;
    @Autowired private RegistrationInviteRepository inviteRepository;
    @Autowired private InviteTokenService inviteTokenService;
    @Autowired private EmailService emailService;
    @Autowired private OtpService otpService;

    @Transactional
    public PreRegisterResponse preRegister(PreRegisterRequest req, Long adminUserId, boolean canAssignElevated) {
        String email = req.email().toLowerCase().strip();

        if (userRepository.existsByEmailIncludingDeleted(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with this email already exists");
        }

        Set<AppRole> roles = resolveRoles(req.roleCodes());
        boolean elevated = roles.stream().anyMatch(r -> !NON_ELEVATED.contains(r.getCode()));
        if (elevated && !canAssignElevated) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only administrators can create staff, instructor or admin accounts");
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
        user.setRoles(roles);
        userRepository.save(user);

        boolean invited = false;
        if (req.sendInvite()) {
            invited = issueAndSendInvite(user, adminUserId);
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
        if (!issueAndSendInvite(user, adminUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The email could not be sent");
        }
        log.info("Resent invite for user {} (id={}) by admin id={}", user.getEmail(), userId, adminUserId);
    }

    private boolean issueAndSendInvite(User user, Long adminUserId) {
        String fullName = user.getProfile() != null
                ? ((user.getProfile().getFirstName() == null ? "" : user.getProfile().getFirstName()) + " "
                        + (user.getProfile().getLastName() == null ? "" : user.getProfile().getLastName())).strip()
                : user.getEmail();
        String roleLabel = user.getRoles().stream().map(AppRole::getCode).sorted()
                .map(c -> c.charAt(0) + c.substring(1).toLowerCase()).collect(Collectors.joining(" / "));
        if (roleLabel.isBlank()) roleLabel = "member";
        String code = otpService.issueInviteCode(user.getEmail());
        try {
            emailService.sendAccountInvite(user.getEmail(), fullName, roleLabel, code);
        } catch (RuntimeException e) {
            // The account exists either way; staff can resend the invite.
            log.warn("Could not email invite to {}: {}", user.getEmail(), e.getMessage());
            return false;
        }
        return true;
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