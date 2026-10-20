package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.controller.dto.HardwareCheckInDTO;
import com.makerspace.backend.model.HardwareCheckIn;
import com.makerspace.backend.model.User;
import com.makerspace.backend.services.HardwareCheckInService;
import com.makerspace.backend.services.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/api/v1/hardware")
public class HardwareCheckInController {

    @Autowired
    private HardwareCheckInService checkInService;

    @Autowired
    private JwtService jwtService;

    /**
     * POST /api/v1/hardware/checkin
     * Raspberry Pi device calls this to register a user check-in
     */
    @PostMapping("/checkin")
    public ResponseEntity<HardwareCheckInDTO> handleCheckIn(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        
        log.info("Hardware check-in request received");
        
        Long userId = getLongFromBody(body, "userId");
        Long equipmentId = getLongFromBody(body, "equipmentId");
        String deviceId = (String) body.get("deviceId");

        if (userId == null || equipmentId == null || deviceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "userId, equipmentId, and deviceId are required");
        }

        HardwareCheckIn checkIn = checkInService.checkIn(userId, equipmentId, deviceId);
        return ResponseEntity.ok(new HardwareCheckInDTO(checkIn));
    }

    /**
     * GET /api/v1/hardware/checkins/pending-waivers
     * Admin: Get all pending waiver approvals
     */
    @GetMapping("/checkins/pending-waivers")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public ResponseEntity<Page<HardwareCheckInDTO>> getPendingWaivers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Fetching pending waivers - page: {}, size: {}", page, size);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<HardwareCheckIn> checkIns = checkInService.getPendingWaivers(pageable);
        return ResponseEntity.ok(checkIns.map(HardwareCheckInDTO::new));
    }

    /**
     * GET /api/v1/hardware/checkins/recent
     * Admin: Get recent check-ins
     */
    @GetMapping("/checkins/recent")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public ResponseEntity<Page<HardwareCheckInDTO>> getRecentCheckIns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Fetching recent check-ins - page: {}, size: {}", page, size);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<HardwareCheckIn> checkIns = checkInService.getRecentCheckIns(pageable);
        return ResponseEntity.ok(checkIns.map(HardwareCheckInDTO::new));
    }

    /**
     * GET /api/v1/hardware/checkins/user/{userId}
     * Get check-ins for a specific user
     */
    @GetMapping("/checkins/user/{userId}")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT') or @jwtService.getCurrentUserId(#request) == #userId")
    public ResponseEntity<Page<HardwareCheckInDTO>> getUserCheckIns(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        
        log.info("Fetching check-ins for user {} - page: {}, size: {}", userId, page, size);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<HardwareCheckIn> checkIns = checkInService.getUserCheckIns(userId, pageable);
        return ResponseEntity.ok(checkIns.map(HardwareCheckInDTO::new));
    }

    /**
     * POST /api/v1/hardware/checkins/{checkInId}/approve-waiver
     * Admin: Approve a signed waiver
     */
    @PostMapping("/checkins/{checkInId}/approve-waiver")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public ResponseEntity<HardwareCheckInDTO> approveWaiver(
            @PathVariable Long checkInId,
            Authentication auth) {
        
        log.info("Approving waiver for check-in {}", checkInId);
        
        HardwareCheckIn checkIn = checkInService.approveWaiver(checkInId, currentUserId(auth));
        return ResponseEntity.ok(new HardwareCheckInDTO(checkIn));
    }

    /**
     * POST /api/v1/hardware/checkins/{checkInId}/deny
     * Admin: Deny access
     */
    @PostMapping("/checkins/{checkInId}/deny")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public ResponseEntity<HardwareCheckInDTO> denyAccess(
            @PathVariable Long checkInId,
            @RequestBody Map<String, String> body) {
        
        String reason = body.get("reason");
        log.info("Denying access for check-in {} - reason: {}", checkInId, reason);
        
        HardwareCheckIn checkIn = checkInService.denyAccess(checkInId, reason);
        return ResponseEntity.ok(new HardwareCheckInDTO(checkIn));
    }

    /**
     * POST /api/v1/hardware/checkins/{checkInId}/sign-waiver
     * User: Sign waiver with rabbit signature
     */
    @PostMapping("/checkins/{checkInId}/sign-waiver")
    public ResponseEntity<HardwareCheckInDTO> signWaiver(
            @PathVariable Long checkInId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        
        String signatureData = body.get("signatureData");
        if (signatureData == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "signatureData is required");
        }
        
        log.info("Signing waiver for check-in {}", checkInId);
        
        boolean isStaff = auth.getAuthorities().stream()
                .anyMatch(a -> "MANAGE_EQUIPMENT".equals(a.getAuthority()));
        HardwareCheckIn checkIn = checkInService.signWaiver(checkInId, signatureData, currentUserId(auth), isStaff);
        return ResponseEntity.ok(new HardwareCheckInDTO(checkIn));
    }

    /**
     * GET /api/v1/hardware/checkins/me/equipment/{equipmentId}
     * The signed-in user's latest waiver request for that equipment (204 if none).
     */
    @GetMapping("/checkins/me/equipment/{equipmentId}")
    public ResponseEntity<HardwareCheckInDTO> myWaiverForEquipment(
            @PathVariable Long equipmentId, Authentication auth) {
        return checkInService.getLatestForEquipment(currentUserId(auth), equipmentId)
                .map(c -> ResponseEntity.ok(new HardwareCheckInDTO(c)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * POST /api/v1/hardware/waiver-requests
     * User signs the equipment waiver during reservation; staff then approve it.
     */
    @PostMapping("/waiver-requests")
    public ResponseEntity<HardwareCheckInDTO> submitWaiver(
            @RequestBody Map<String, Object> body, Authentication auth) {
        Long equipmentId = getLongFromBody(body, "equipmentId");
        Object signature = body.get("signatureData");
        if (equipmentId == null || !(signature instanceof String signatureData)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "equipmentId and signatureData are required");
        }
        HardwareCheckIn checkIn = checkInService.submitWaiver(currentUserId(auth), equipmentId, signatureData);
        return ResponseEntity.ok(new HardwareCheckInDTO(checkIn));
    }

    /**
     * GET /api/v1/hardware/checkins/{checkInId}/signature
     * Admin: view the signature captured for a waiver
     */
    @GetMapping("/checkins/{checkInId}/signature")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public ResponseEntity<Map<String, String>> getSignature(@PathVariable Long checkInId) {
        return ResponseEntity.ok(Map.of("signatureData", checkInService.getSignature(checkInId)));
    }

    /**
     * GET /api/v1/hardware/checkins/{checkInId}
     * Get a specific check-in by ID
     */
    @GetMapping("/checkins/{checkInId}")
    public ResponseEntity<HardwareCheckInDTO> getCheckIn(@PathVariable Long checkInId, Authentication auth) {
        boolean isStaff = auth.getAuthorities().stream()
                .anyMatch(a -> "MANAGE_EQUIPMENT".equals(a.getAuthority()));
        return ResponseEntity.ok(new HardwareCheckInDTO(
                checkInService.getForUser(checkInId, currentUserId(auth), isStaff)));
    }

    private Long getLongFromBody(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private User extractUserFromToken(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No token provided");
        }
        // In a real implementation, extract the user from token
        // For now, return a placeholder
        return new User();
    }

    private Long currentUserId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            return up.userId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
