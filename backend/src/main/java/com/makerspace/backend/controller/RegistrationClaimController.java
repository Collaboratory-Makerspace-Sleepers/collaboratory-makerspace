package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.controller.dto.ClaimRequest;
import com.makerspace.backend.controller.dto.UserDTO;
import com.makerspace.backend.services.AccountClaimService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationClaimController {

    private final AccountClaimService claimService;

    public RegistrationClaimController(AccountClaimService claimService) {
        this.claimService = claimService;
    }

    /**
     * Consumes an invite token and activates the caller's pre-registered account.
     * Requires authentication (ROLE_PENDING is sufficient).
     * After a successful claim the client should re-authenticate to get a JWT with full roles.
     */
    @PostMapping("/claim")
    public UserDTO claim(@Valid @RequestBody ClaimRequest req, Authentication auth) {
        return claimService.claim((UserPrincipal) auth.getPrincipal(), req.token());
    }
}