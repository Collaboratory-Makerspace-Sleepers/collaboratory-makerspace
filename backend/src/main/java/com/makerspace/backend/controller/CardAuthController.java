package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.OAuthProfile;
import com.makerspace.backend.model.UnregisteredCard;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserResolution;
import com.makerspace.backend.services.CardService;
import com.makerspace.backend.services.JwtService;
import com.makerspace.backend.services.UserService;
import com.makerspace.backend.services.UserStateService;
import com.makerspace.backend.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

/**
 * Card-based authentication flow for unregistered cards.
 * 
 * Flow:
 * 1. User taps new card at reader (UnregisteredCard created with status PENDING)
 * 2. Admin assigns email, role, and sends OTP via /api/v1/hardware/unregistered-cards endpoints
 * 3. User receives email with OTP code (NOT a dynamically generated code)
 * 4. User enters email + OTP here to sign in
 * 5. System verifies OTP against the admin-sent code
 * 6. User gets JWT token
 * 7. System creates MemberCard and activates card access
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/auth/card")
public class CardAuthController {

    @Autowired private CardService cardService;
    @Autowired private UserService userService;
    @Autowired private UserStateService userStateService;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    @Value("${app.jwt.expiration:3600000}")
    private int jwtExpiration;

    /**
     * Verify the admin-sent OTP code for card registration.
     * This endpoint is used ONLY for unregistered card sign-in.
     * It does NOT generate a new code - only verifies the admin-sent code.
     * 
     * POST /api/v1/auth/card/verify-otp
     * Request: { "email": "user@example.com", "otp": "123456" }
     * Response: { "token": "JWT_TOKEN" }
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, String>> verifyCardOtp(
            @RequestBody Map<String, String> body,
            HttpServletResponse response) {
        
        String email = body.get("email");
        String otp = body.get("otp");
        
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        if (otp == null || otp.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "otp is required");
        }

        email = email.trim().toLowerCase();

        // Find and verify the unregistered card OTP
        UnregisteredCard unregistered = cardService.verifyOtp(otp.trim(), email);
        if (unregistered == null) {
            log.warn("OTP verification failed for email: {}", email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired OTP");
        }

        // OTP proves email ownership — use same OAuth resolution logic
        String otpSubject = "card:" + email;
        OAuthProfile profile = new OAuthProfile(email, email, "", otpSubject);
        UserResolution resolution = userService.resolve(profile);

        User user = switch (resolution) {
            case UserResolution.Active active -> active.user();

            case UserResolution.Pending pending ->
                // Email verified — auto-activate the pre-registered account
                userStateService.autoClaimByEmail(pending.user().getId(), otpSubject);

            case UserResolution.NotFound notFound ->
                // Self-provision as new user
                userService.provision(notFound.profile());

            case UserResolution.Deleted ignored ->
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account_closed");
        };

        // Generate JWT token
        String token = jwtService.generateToken(user, otpSubject);

        // Set JWT cookie
        Cookie cookie = new Cookie("access_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(jwtExpiration / 1000);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);

        log.info("Card OTP verification succeeded for email {}", email);
        
        return ResponseEntity.ok(Map.of("access_token", token));
    }

    /**
     * Activate the card after OTP verification and user signs in.
     * This is called when the user taps their card at the reader after successful OTP verification.
     * 
     * POST /api/v1/auth/card/activate
     * Request: { "cardUid": "ABC123DEF456" }
     * Response: { "success": "true", "message": "Card activated with ADMIN access" }
     */
    @PostMapping("/activate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> activateCard(
            @RequestBody Map<String, String> body) {
        
        String cardUid = body.get("cardUid");
        if (cardUid == null || cardUid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cardUid is required");
        }

        // Get authenticated user from SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        // Get the principal - should be a UserDetails or custom authentication object
        Object principal = auth.getPrincipal();
        String userEmail = null;
        
        // Handle different types of principals
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            // If using standard UserDetails, extract email from username
            userEmail = userDetails.getUsername();
        } else if (principal instanceof String strEmail) {
            // If principal is email directly
            userEmail = strEmail;
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to extract user from authentication");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        try {
            // Activate the card with the user's ID
            cardService.activateCardAfterOtp(cardUid.trim(), user.getId());
            
            log.info("Card {} activated for user {}", cardUid, userEmail);
            
            // Determine role for response
            String role = cardService.getUnregisteredCardByUid(cardUid.trim())
                    .map(c -> c.getAssignedRole() != null ? c.getAssignedRole() : "MEMBER")
                    .orElse("MEMBER");
            
            return ResponseEntity.ok(Map.of(
                "success", "true",
                "message", "Card activated with " + role + " access",
                "role", role
            ));
        } catch (Exception e) {
            log.error("Card activation failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Failed to activate card: " + e.getMessage());
        }
    }

    /**
     * Send OTP code to user's email after card is assigned.
     * This is called by the USER (not admin) after their card is assigned by admin.
     * 
     * POST /api/v1/auth/card/send-otp
     * Request: { "email": "user@example.com", "cardUid": "ABC123DEF456" }
     * Response: { "success": "true", "message": "Code sent to your email" }
     */
    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, String>> sendCardOtp(
            @RequestBody Map<String, String> body) {
        
        String email = body.get("email");
        String cardUid = body.get("cardUid");
        
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        if (cardUid == null || cardUid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cardUid is required");
        }

        email = email.trim().toLowerCase();
        cardUid = cardUid.trim();

        // Find the unregistered card
        var card = cardService.getUnregisteredCardByUid(cardUid);
        if (card.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
        }

        UnregisteredCard unregistered = card.get();

        // Verify email matches the assigned email
        String assignedEmail = unregistered.getAssignedToEmail();
        if (assignedEmail == null || !assignedEmail.equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email does not match assigned email for this card");
        }

        // Verify card has been assigned (status ASSIGNED or higher)
        if (!("ASSIGNED".equals(unregistered.getStatus()) || 
              "ROLE_ASSIGNED".equals(unregistered.getStatus()) ||
              "VERIFIED".equals(unregistered.getStatus()) ||
              "ACTIVATED".equals(unregistered.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card has not been assigned by admin yet");
        }

        try {
            // Generate and send OTP
            cardService.generateAndSendOtp(unregistered);
            
            log.info("OTP sent to email {} for card {}", email, cardUid);
            
            return ResponseEntity.ok(Map.of(
                "success", "true",
                "message", "Code sent to your email"
            ));
        } catch (Exception e) {
            log.error("Failed to send OTP: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Failed to send code. Please try again.");
        }
    }

    /**
     * Check if a card needs OTP verification.
     * This is called BEFORE the user attempts to sign in to verify the card exists.
     * 
     * GET /api/v1/auth/card/check-by-uid?uid=ABC123DEF456
     * Response: { "found": true, "email": "user@example.com", "needsOtp": true }
     */
    @GetMapping("/check-by-uid")
    public ResponseEntity<Map<String, Object>> checkCardByUid(@RequestParam String uid) {
        if (uid == null || uid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "uid is required");
        }

        var card = cardService.getUnregisteredCardByUid(uid.trim());
        
        if (card.isEmpty()) {
            // Card not found in unregistered list, check if it's already a member card
            return ResponseEntity.ok(Map.of(
                "found", false,
                "message", "Card not found or already registered"
            ));
        }

        UnregisteredCard unregistered = card.get();
        
        return ResponseEntity.ok(Map.of(
            "found", true,
            "email", unregistered.getAssignedToEmail() != null ? unregistered.getAssignedToEmail() : "",
            "status", unregistered.getStatus(),
            "canSendOtp", "ASSIGNED".equals(unregistered.getStatus()) || 
                          "ROLE_ASSIGNED".equals(unregistered.getStatus()) ||
                          "VERIFIED".equals(unregistered.getStatus()),
            "otpGenerated", unregistered.getOtpGeneratedAt() != null
        ));
    }
}
