package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.UserSecurity;
import com.makerspace.backend.controller.dto.PreRegisterRequest;
import com.makerspace.backend.controller.dto.PreRegisterResponse;
import com.makerspace.backend.services.AdminRegistrationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/registrations")
public class AdminRegistrationController {

    @Autowired private AdminRegistrationService registrationService;
    @Autowired private UserSecurity userSecurity;

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<PreRegisterResponse> preRegister(
            @Valid @RequestBody PreRegisterRequest req,
            Authentication auth) {
        PreRegisterResponse res = registrationService.preRegister(req, userSecurity.getUserId(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @PostMapping("/{userId}/resend-invite")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<Void> resendInvite(@PathVariable Long userId, Authentication auth) {
        registrationService.resendInvite(userId, userSecurity.getUserId(auth));
        return ResponseEntity.noContent().build();
    }

}
