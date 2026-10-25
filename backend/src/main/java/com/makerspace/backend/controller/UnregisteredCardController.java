package com.makerspace.backend.controller;

import com.makerspace.backend.model.UnregisteredCard;
import com.makerspace.backend.services.CardService;
import com.makerspace.backend.services.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/api/v1/hardware/unregistered-cards")
public class UnregisteredCardController {

    @Autowired private CardService cardService;
    @Autowired private EmailService emailService;

    /**
     * GET /api/v1/hardware/unregistered-cards
     * Admin endpoint to view all pending and assigned unregistered cards.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public List<Map<String, Object>> listUnregistered() {
        return cardService.listUnregisteredCards().stream().map(this::cardJson).toList();
    }

    /**
     * POST /api/v1/hardware/unregistered-cards/{id}/assign
     * Admin assigns an unregistered card to an email address.
     * Request body: { "email": "user@example.com" }
     */
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public Map<String, Object> assignToEmail(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!(body.get("email") instanceof String email) || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        UnregisteredCard card = cardService.assignToEmail(id, email.trim());
        return cardJson(card);
    }

    /**
     * POST /api/v1/hardware/unregistered-cards/{id}/assign-role
     * Admin assigns a role to the unregistered card.
     * Request body: { "role": "ADMIN" | "STAFF" | "MEMBER" }
     */
    @PostMapping("/{id}/assign-role")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public Map<String, Object> assignRole(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!(body.get("role") instanceof String role) || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role is required");
        }
        UnregisteredCard card = cardService.assignRole(id, role.trim());
        return cardJson(card);
    }

    /**
     * POST /api/v1/hardware/unregistered-cards/{id}/activate-directly
     * Admin activates a card immediately with email + role assignment.
     * No email/OTP verification required - card works on next tap.
     * Request body: { "userId": 123, "role": "MEMBER" }
     */
    @PostMapping("/{id}/activate-directly")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public Map<String, Object> activateDirectly(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                 org.springframework.security.core.Authentication auth) {
        if (!(body.get("userId") instanceof Number userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        String role = body.get("role") instanceof String r ? r.trim() : "MEMBER";
        
        Long staffId = null;
        if (auth != null && auth.getPrincipal() instanceof com.makerspace.backend.config.security.UserPrincipal principal) {
            staffId = principal.userId();
        }
        
        var card = cardService.activateCardDirectly(id, userId.longValue(), role, staffId);
        
        // Also update the unregistered card with email from user if it has one
        try {
            var unregistered = cardService.listUnregisteredCards().stream()
                    .filter(c -> c.getCardUid().equals(card.getCardUid()))
                    .findFirst();
            if (unregistered.isPresent()) {
                return cardJson(unregistered.get());
            }
        } catch (Exception e) {
            log.warn("Could not update unregistered card info after activation: {}", e.getMessage());
        }
        
        return Map.of(
            "success", true,
            "cardUid", card.getCardUid(),
            "userId", card.getUser().getId(),
            "message", "Card activated successfully - user can now tap to access"
        );
    }

    /**
     * POST /api/v1/hardware/unregistered-cards/{id}/send-otp
     * Admin sends OTP code to the assigned email.
     * This triggers an email to be sent with the OTP.
     */
    @PostMapping("/{id}/send-otp")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public Map<String, Object> sendOtp(@PathVariable Long id) {
        UnregisteredCard card = cardService.generateOtp(id);
        
        // Send OTP email
        if (card.getAssignedToEmail() != null && card.getOtpCode() != null) {
            try {
                emailService.sendCardActivationOtp(card.getAssignedToEmail(), card.getOtpCode(), card.getCardUid());
                log.info("OTP sent to {} for card {}", card.getAssignedToEmail(), card.getCardUid());
            } catch (Exception e) {
                log.error("Failed to send OTP email to {}: {}", card.getAssignedToEmail(), e.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send OTP email");
            }
        }
        
        return cardJson(card);
    }

    /**
     * POST /api/v1/hardware/unregistered-cards/verify-otp
     * User verifies their OTP (called after receiving email).
     * Request body: { "otp": "123456" }
     * Requires authentication as the user whose email this card is assigned to.
     */
    @PostMapping("/verify-otp")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> verifyOtp(@RequestBody Map<String, Object> body,
                                          @RequestHeader(value = "Authorization") String auth) {
        if (!(body.get("otp") instanceof String otp) || otp.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "otp is required");
        }
        
        // Note: In a real implementation, you'd extract userId from JWT token
        // For now, this is a simplified version. See AuthContext for proper JWT handling.
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "Use /api/v1/auth/verify-card-otp instead, which handles JWT token extraction");
    }

    /**
     * POST /api/v1/hardware/unregistered-cards/activate-after-tap
     * After user taps the card again (following successful OTP verification),
     * this endpoint creates the MemberCard entry.
     * Called by the system after verifying the user is logged in and OTP was verified.
     */
    @PostMapping("/activate-after-tap")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> activateCardAfterTap(@RequestBody Map<String, Object> body) {
        if (!(body.get("cardUid") instanceof String cardUid) || cardUid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cardUid is required");
        }
        // This would be called after OTP verification to finalize the card setup
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "Call /api/v1/auth/activate-card instead");
    }

    /**
     * DELETE /api/v1/hardware/unregistered-cards/{id}
     * Admin deletes/deactivates an unregistered card for testing/reset purposes.
     * Allows admins to clear cards and restart the registration process.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public ResponseEntity<Map<String, String>> deleteCard(@PathVariable Long id) {
        try {
            cardService.deleteUnregisteredCard(id);
            return ResponseEntity.ok(Map.of(
                "success", "true",
                "message", "Card deactivated and removed from queue"
            ));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found: " + e.getMessage());
        }
    }

    private Map<String, Object> cardJson(UnregisteredCard c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("cardUid", c.getCardUid());
        m.put("status", c.getStatus());
        m.put("assignedToEmail", c.getAssignedToEmail());
        m.put("assignedAt", c.getAssignedAt());
        m.put("assignedRole", c.getAssignedRole());
        m.put("roleAssignedAt", c.getRoleAssignedAt());
        m.put("otpGenerated", c.getOtpGeneratedAt() != null);
        m.put("otpVerified", c.isOtpVerified());
        m.put("deviceId", c.getDeviceId());
        m.put("scanCount", c.getScanCount());
        m.put("firstScannedAt", c.getFirstScannedAt());
        m.put("lastScannedAt", c.getLastScannedAt());
        return m;
    }
}
