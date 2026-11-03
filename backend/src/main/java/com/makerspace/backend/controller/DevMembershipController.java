package com.makerspace.backend.controller;

import com.makerspace.backend.model.*;
import com.makerspace.backend.repository.MembershipPlanRepository;
import com.makerspace.backend.repository.MembershipRepository;
import com.makerspace.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;

/**
 * Development-only controller for testing membership and billing flows.
 * Allows manual creation of memberships without waiting for Stripe webhooks.
 * This is only available in the 'dev' profile.
 */
@Profile("dev")
@Slf4j
@RestController
@RequestMapping("/api/dev/membership")
public class DevMembershipController {

    @Autowired private MembershipRepository membershipRepository;
    @Autowired private MembershipPlanRepository membershipPlanRepository;
    @Autowired private UserRepository userRepository;

    /**
     * Manually create an ACTIVE membership for the current user.
     * Useful for testing reservation flows without waiting for Stripe webhooks.
     */
    @PostMapping("/create-active-for-testing")
    @ResponseStatus(HttpStatus.CREATED)
    public void createActiveMembershipForTesting(@RequestParam(required = false) String planCode, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String code = planCode != null ? planCode : "MONTHLY";
        MembershipPlan plan = membershipPlanRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "Plan not found: " + code + ". Available: MONTHLY, ANNUAL, STUDENT, DAY_PASS"));

        // Check for existing membership
        var existing = membershipRepository.findByUserId(user.getId());
        if (existing.isPresent()) {
            Membership m = existing.get();
            m.setStatus(MembershipStatus.ACTIVE);
            m.setCurrentPeriodStart(ZonedDateTime.now());
            m.setCurrentPeriodEnd(ZonedDateTime.now().plusMonths(1));
            m.setStripeSubscriptionId("test_" + System.currentTimeMillis());
            m.setStripeUpdatedAt(ZonedDateTime.now());
            membershipRepository.save(m);
            log.info("✅ Updated existing membership to ACTIVE for user {} ({})", user.getId(), email);
            return;
        }

        // Create new membership
        Membership membership = new Membership();
        membership.setUser(user);
        membership.setPlan(plan);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setCurrentPeriodStart(ZonedDateTime.now());
        membership.setCurrentPeriodEnd(ZonedDateTime.now().plusMonths(1));
        membership.setStripeSubscriptionId("test_" + System.currentTimeMillis());
        membership.setStripeUpdatedAt(ZonedDateTime.now());
        
        membershipRepository.save(membership);
        log.info("✅ Created ACTIVE {} membership for user {} ({})", code, user.getId(), email);
    }

    /**
     * Check the current user's membership status.
     */
    @GetMapping("/check")
    public MembershipCheckResponse checkMembership(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        var membership = membershipRepository.findByUserId(user.getId());
        
        return new MembershipCheckResponse(
            user.getId(),
            email,
            membership.map(m -> new MembershipInfo(
                m.getStatus().toString(),
                m.getCurrentPeriodStart(),
                m.getCurrentPeriodEnd()
            )).orElse(null)
        );
    }

    /**
     * Delete membership for the current user (cleanup for testing).
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMembershipForTesting(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        var membership = membershipRepository.findByUserId(user.getId());
        if (membership.isPresent()) {
            membershipRepository.delete(membership.get());
            log.info("✅ Deleted membership for user {} ({})", user.getId(), email);
        }
    }

    // DTOs for responses
    record MembershipCheckResponse(
        Long userId,
        String email,
        MembershipInfo membership
    ) {}

    record MembershipInfo(
        String status,
        ZonedDateTime periodStart,
        ZonedDateTime periodEnd
    ) {}
}
