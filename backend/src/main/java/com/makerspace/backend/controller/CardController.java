package com.makerspace.backend.controller;

import com.makerspace.backend.model.CardScan;
import com.makerspace.backend.model.MemberCard;
import com.makerspace.backend.model.User;
import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.services.CardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/api/v1/hardware")
public class CardController {

    @Autowired private CardService cardService;

    /** When set, the Pi must send it as X-Device-Key. Unset keeps local development open. */
    @Value("${app.hardware.device-key:}")
    private String deviceKey;

    /**
     * POST /api/v1/hardware/card-scan - called by the Raspberry Pi with the card UID.
     * Always answers 200 with approved true/false so the reader can light green or red.
     */
    @PostMapping("/card-scan")
    public ResponseEntity<Map<String, Object>> scan(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Device-Key", required = false) String key) {
        if (!deviceKey.isBlank() && !deviceKey.equals(key)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device key");
        }
        Object uid = body.get("cardUid");
        if (!(uid instanceof String cardUid)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cardUid is required");
        }
        String deviceId = body.get("deviceId") instanceof String d ? d : null;
        Long equipmentId = body.get("equipmentId") instanceof Number n ? n.longValue() : null;

        CardService.ScanResult r = cardService.scan(cardUid, deviceId, equipmentId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("approved", r.approved());
        out.put("status", r.approved() ? "APPROVED" : "DENIED");
        out.put("message", r.message());
        out.put("memberName", r.approved() ? r.memberName() : null);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/cards")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public List<Map<String, Object>> cards() {
        return cardService.listCards().stream().map(this::cardJson).toList();
    }

    @PostMapping("/cards")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public Map<String, Object> assign(@RequestBody Map<String, Object> body, Authentication auth) {
        if (!(body.get("cardUid") instanceof String uid) || !(body.get("userId") instanceof Number userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cardUid and userId are required");
        }
        String label = body.get("label") instanceof String l && !l.isBlank() ? l.trim() : null;
        return cardJson(cardService.assign(uid, userId.longValue(), label,
                ((UserPrincipal) auth.getPrincipal()).userId()));
    }

    @PatchMapping("/cards/{id}")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") instanceof Number n ? n.longValue() : null;
        Boolean active = body.get("active") instanceof Boolean b ? b : null;
        String label = body.get("label") instanceof String l ? l : null;
        return cardJson(cardService.update(id, userId, active, label));
    }

    @DeleteMapping("/cards/{id}")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cardService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/card-scans/recent")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public List<Map<String, Object>> recent(@RequestParam(defaultValue = "25") int limit) {
        return cardService.recentScans(limit).stream().map(this::scanJson).toList();
    }

    @GetMapping("/cards/members")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public List<Map<String, Object>> members(@RequestParam(defaultValue = "") String q) {
        return cardService.searchMembers(q).stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("name", CardService.displayName(u));
            m.put("email", u.getEmail());
            return m;
        }).toList();
    }

    private Map<String, Object> cardJson(MemberCard c) {
        User u = c.getUser();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("cardUid", c.getCardUid());
        m.put("userId", u.getId());
        m.put("memberName", CardService.displayName(u));
        m.put("memberEmail", u.getEmail());
        m.put("label", c.getLabel());
        m.put("active", c.isActive());
        m.put("assignedAt", c.getAssignedAt());
        m.put("lastSeenAt", c.getLastSeenAt());
        return m;
    }

    private Map<String, Object> scanJson(CardScan s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("cardUid", s.getCardUid());
        m.put("approved", s.isApproved());
        m.put("reason", s.getReason());
        m.put("memberName", s.getUser() == null ? null : CardService.displayName(s.getUser()));
        m.put("equipmentName", s.getEquipment() == null ? null : s.getEquipment().getName());
        m.put("deviceId", s.getDeviceId());
        m.put("scannedAt", s.getScannedAt());
        return m;
    }
}
