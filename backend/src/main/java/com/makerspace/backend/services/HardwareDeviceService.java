package com.makerspace.backend.services;

import com.makerspace.backend.config.security.DevicePrincipal;
import com.makerspace.backend.model.*;
import com.makerspace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class HardwareDeviceService {
    private final HardwareDeviceRepository devices;
    private final HardwareDeviceEventRepository events;
    private final EquipmentRepository equipment;
    private final UserRepository users;
    private final MemberCardRepository cards;
    private final CardService cardService;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.jwt.secret}") private String pairingSecret;

    public record DeviceView(UUID id, String name, String area, Long equipmentId, boolean enabled,
                             boolean doorEnabled, int unlockSeconds, Instant lastSeenAt, boolean connected,
                             String hostname, String readerStatus, Instant pairingExpiresAt,
                             Long assignmentUserId, Instant assignmentExpiresAt) {}
    public record DeviceSettings(String name, String area, Long equipmentId, Boolean enabled,
                                 Boolean doorEnabled, Integer unlockSeconds) {}
    public record Pairing(DeviceView device, String code, Instant expiresAt) {}
    public record EventInput(UUID eventId, Instant occurredAt, String type, String detail,
                             String outcome, String cardUid, Long scanId) {}
    public record EventView(Long id, UUID deviceId, String deviceName, String type, String detail,
                            String outcome, String source, Instant occurredAt, Instant receivedAt, String cardUid, Long scanId) {}

    public static String keyHash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String pairingHash(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pairingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot hash pairing code", ex);
        }
    }

    private static String text(String value, int max, String field, boolean required) {
        if (value == null) {
            if (required) throw bad(field + " is required");
            return "";
        }
        String trimmed = value.trim();
        if ((required && trimmed.isEmpty()) || trimmed.length() > max) {
            throw bad(field + " must contain " + (required ? "1-" : "0-") + max + " characters");
        }
        return trimmed;
    }

    private static ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private DeviceView view(HardwareDevice d) {
        boolean assigning = d.getAssignmentExpiresAt() != null && d.getAssignmentExpiresAt().isAfter(Instant.now());
        return new DeviceView(d.getId(), d.getName(), d.getArea(), d.getEquipmentId(), d.isEnabled(),
                d.isDoorEnabled(), d.getUnlockSeconds(), d.getLastSeenAt(), d.isConnected(), d.getHostname(),
                d.getReaderStatus(), d.getPairingExpiresAt(), assigning ? d.getAssignmentUserId() : null,
                assigning ? d.getAssignmentExpiresAt() : null);
    }

    @Transactional(readOnly = true)
    public List<DeviceView> list() {
        return devices.findByDeletedAtIsNullOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    private HardwareDevice locked(UUID id) {
        return devices.findLocked(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Pi not found"));
    }

    private void settings(HardwareDevice d, DeviceSettings input) {
        if (input.name() != null) d.setName(text(input.name(), 100, "Name", true));
        if (input.area() != null) d.setArea(text(input.area(), 100, "Area", true));
        text(d.getName(), 100, "Name", true);
        text(d.getArea(), 100, "Area", true);
        if (input.equipmentId() != null && !equipment.existsById(input.equipmentId())) {
            throw bad("Equipment not found");
        }
        d.setEquipmentId(input.equipmentId());
        if (input.enabled() != null) d.setEnabled(input.enabled());
        if (input.doorEnabled() != null) d.setDoorEnabled(input.doorEnabled());
        if (input.unlockSeconds() != null) {
            if (input.unlockSeconds() < 1 || input.unlockSeconds() > 30) throw bad("Unlock duration must be 1-30 seconds");
            d.setUnlockSeconds(input.unlockSeconds());
        }
        if (!d.isEnabled()) {
            d.setConnected(false);
            clearAssignment(d);
        }
    }

    public Pairing create(DeviceSettings input) {
        HardwareDevice d = new HardwareDevice();
        settings(d, input);
        d = devices.saveAndFlush(d);
        return issueCode(d);
    }

    public DeviceView update(UUID id, DeviceSettings input) {
        HardwareDevice d = locked(id);
        settings(d, input);
        audit(d, "DEVICE_UPDATED", "Pi settings updated", "INFO", null, null);
        return view(d);
    }

    public Pairing pairingCode(UUID id) {
        return issueCode(locked(id));
    }

    private Pairing issueCode(HardwareDevice d) {
        String code = String.format(Locale.ROOT, "%08d", random.nextInt(100_000_000));
        d.setKeyHash(null);
        d.setPairingHash(pairingHash(code));
        d.setPairingExpiresAt(Instant.now().plusSeconds(600));
        d.setConnected(false);
        clearAssignment(d);
        audit(d, "PAIRING_CODE_CREATED", "Existing credentials revoked; pairing expires in ten minutes", "INFO", null, null);
        return new Pairing(view(d), code, d.getPairingExpiresAt());
    }

    public void delete(UUID id) {
        HardwareDevice d = locked(id);
        d.setDeletedAt(Instant.now());
        d.setEnabled(false);
        d.setConnected(false);
        d.setKeyHash(null);
        d.setPairingHash(null);
        d.setPairingExpiresAt(null);
        clearAssignment(d);
        audit(d, "DEVICE_DELETED", "Pi registration revoked", "INFO", null, null);
    }

    public Map<String, Object> pair(String code, String hostname) {
        if (code == null || !code.matches("[0-9]{8}")) throw invalidPair();
        HardwareDevice d = devices.findForPairing(pairingHash(code)).orElseThrow(this::invalidPair);
        if (!d.isEnabled() || d.getPairingExpiresAt() == null || !d.getPairingExpiresAt().isAfter(Instant.now())) {
            throw invalidPair();
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String key = HexFormat.of().formatHex(bytes);
        d.setKeyHash(keyHash(key));
        d.setPairingHash(null);
        d.setPairingExpiresAt(null);
        d.setHostname(text(hostname, 100, "Hostname", true));
        touch(d);
        audit(d, "DEVICE_PAIRED", "Pi paired successfully", "INFO", null, null);
        return Map.of("id", d.getId().toString(), "deviceKey", key, "name", d.getName(),
                "enabled", d.isEnabled(), "doorEnabled", d.isDoorEnabled());
    }

    private ResponseStatusException invalidPair() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired Pi pairing code");
    }

    @Transactional(readOnly = true)
    public Optional<DevicePrincipal> authenticate(String id, String key) {
        if (id == null || key == null || !key.matches("[0-9a-f]{64}")) return Optional.empty();
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        String hash = keyHash(key);
        return devices.findById(uuid).filter(d -> valid(d, hash)).map(d -> new DevicePrincipal(uuid, hash));
    }

    private boolean valid(HardwareDevice d, String hash) {
        return d.getDeletedAt() == null && d.getKeyHash() != null
                && MessageDigest.isEqual(d.getKeyHash().getBytes(StandardCharsets.US_ASCII),
                hash.getBytes(StandardCharsets.US_ASCII));
    }

    private HardwareDevice authenticated(DevicePrincipal principal) {
        HardwareDevice d = devices.findLocked(principal.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Pi registration revoked"));
        if (!valid(d, principal.keyHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Pi credentials revoked");
        }
        return d;
    }

    private void touch(HardwareDevice d) {
        if (!d.isConnected()) audit(d, "CONNECTION_RESTORED", "Pi contacted backend", "INFO", null, null);
        d.setConnected(true);
        d.setLastSeenAt(Instant.now());
    }

    public Map<String, Object> heartbeat(DevicePrincipal principal, String hostname, String readerStatus) {
        HardwareDevice d = authenticated(principal);
        d.setHostname(text(hostname, 100, "Hostname", true));
        d.setReaderStatus(text(readerStatus, 160, "Reader status", true));
        touch(d);
        return Map.of("enabled", d.isEnabled(), "name", d.getName(), "area", d.getArea(),
                "doorEnabled", d.isEnabled() && d.isDoorEnabled(), "unlockSeconds", d.getUnlockSeconds());
    }

    public DeviceView startAssignment(UUID id, Long userId, String label, Long staffId) {
        HardwareDevice d = locked(id);
        if (!d.isEnabled() || d.getKeyHash() == null) throw bad("Pair and enable this Pi before assigning a card");
        if (userId == null || users.findById(userId).filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE).isEmpty()) {
            throw bad("Choose an active member");
        }
        if (d.getAssignmentExpiresAt() != null && d.getAssignmentExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A card assignment is already waiting on this Pi");
        }
        d.setAssignmentUserId(userId);
        d.setAssignmentStaffId(staffId);
        d.setAssignmentLabel(text(label, 100, "Card label", false));
        d.setAssignmentExpiresAt(Instant.now().plusSeconds(120));
        audit(d, "ASSIGNMENT_STARTED", "Waiting for the next tap; expires in two minutes", "INFO", null, null);
        return view(d);
    }

    public DeviceView cancelAssignment(UUID id) {
        HardwareDevice d = locked(id);
        clearAssignment(d);
        audit(d, "ASSIGNMENT_CANCELLED", "Pending card assignment cancelled", "INFO", null, null);
        return view(d);
    }

    private void clearAssignment(HardwareDevice d) {
        d.setAssignmentUserId(null);
        d.setAssignmentStaffId(null);
        d.setAssignmentLabel(null);
        d.setAssignmentExpiresAt(null);
    }

    public Map<String, Object> scan(DevicePrincipal principal, String rawUid) {
        HardwareDevice d = authenticated(principal);
        String uid = validateUid(rawUid);
        touch(d);
        if (!d.isEnabled()) {
            audit(d, "ACCESS_DENIED", "Pi disabled by administrator", "INFO", uid, null);
            return Map.of("approved", false, "status", "DENIED", "message", "Pi disabled by administrator",
                    "door", Map.of("enabled", false));
        }
        if (d.getAssignmentExpiresAt() != null && !d.getAssignmentExpiresAt().isAfter(Instant.now())) {
            clearAssignment(d);
            audit(d, "ASSIGNMENT_EXPIRED", "No card assigned before expiry", "INFO", null, null);
            return assignmentResult("ASSIGNMENT_EXPIRED", "Card assignment expired. Ask staff to start again.");
        }
        if (d.getAssignmentUserId() != null) {
            MemberCard existing = cards.findByCardUid(uid).orElse(null);
            if (existing != null && !existing.getUser().getId().equals(d.getAssignmentUserId())) {
                audit(d, "ASSIGNMENT_REJECTED", "Card belongs to a different member", "ERROR", uid, null);
                return assignmentResult("ASSIGNMENT_REJECTED", "Card belongs to a different member. Use another card.");
            }
            if (users.findById(d.getAssignmentUserId()).filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE).isEmpty()) {
                clearAssignment(d);
                audit(d, "ASSIGNMENT_REJECTED", "Selected member is no longer active", "ERROR", uid, null);
                return assignmentResult("ASSIGNMENT_REJECTED", "Member is no longer active. Ask staff to start again.");
            }
            cardService.assign(uid, d.getAssignmentUserId(), d.getAssignmentLabel(), d.getAssignmentStaffId());
            clearAssignment(d);
            audit(d, "CARD_ASSIGNED", "Card assigned; remove and tap again to check access", "INFO", uid, null);
            return assignmentResult("CARD_ASSIGNED", "Card assigned. Remove and tap again to check access.");
        }
        CardService.ScanResult result = cardService.scan(uid, d.getId().toString(), d.getEquipmentId());
        audit(d, result.approved() ? "ACCESS_APPROVED" : "ACCESS_DENIED", result.message(),
                "INFO", uid, result.scanId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("approved", result.approved());
        out.put("status", result.approved() ? "APPROVED" : "DENIED");
        out.put("message", result.message());
        out.put("memberName", result.memberName());
        out.put("scanId", result.scanId());
        out.put("door", Map.of("enabled", result.approved() && d.isDoorEnabled(),
                "unlockSeconds", d.getUnlockSeconds()));
        return out;
    }

    private Map<String, Object> assignmentResult(String status, String message) {
        return Map.of("approved", false, "enrollment", true, "status", status,
                "message", message, "door", Map.of("enabled", false));
    }

    private String validateUid(String uid) {
        if (uid == null || !uid.matches("(?i)[0-9a-f]{4,32}") || uid.length() % 2 != 0) {
            throw bad("cardUid must contain 4-32 hexadecimal characters in complete bytes");
        }
        return uid.toUpperCase(Locale.ROOT);
    }

    public Map<String, Object> ingest(DevicePrincipal principal, List<EventInput> input) {
        HardwareDevice d = authenticated(principal);
        if (input == null || input.isEmpty() || input.size() > 20) throw bad("Send 1-20 events per batch");
        List<String> accepted = new ArrayList<>();
        for (EventInput item : input) {
            if (item == null || item.eventId() == null || item.occurredAt() == null) throw bad("Event ID and timestamp are required");
            String type = text(item.type(), 80, "Event type", true);
            String detail = text(item.detail(), 400, "Event detail", false);
            if (!Set.of("INFO", "ERROR").contains(item.outcome() == null ? "" : item.outcome())) throw bad("Invalid event outcome");
            String uid = item.cardUid() == null ? null : validateUid(item.cardUid());
            if (!events.existsByDeviceIdAndEventId(d.getId(), item.eventId())) {
                HardwareDeviceEvent event = new HardwareDeviceEvent();
                event.setDeviceId(d.getId());
                event.setEventId(item.eventId());
                event.setType(type);
                event.setDetail(detail);
                event.setOutcome(item.outcome());
                event.setSource("DEVICE");
                event.setCardUid(uid);
                event.setScanId(item.scanId());
                event.setOccurredAt(item.occurredAt());
                events.saveAndFlush(event);
            }
            accepted.add(item.eventId().toString());
        }
        touch(d);
        return Map.of("accepted", accepted);
    }

    private void audit(HardwareDevice d, String type, String detail, String outcome, String uid, Long scanId) {
        HardwareDeviceEvent event = new HardwareDeviceEvent();
        event.setDeviceId(d.getId());
        event.setEventId(UUID.randomUUID());
        event.setType(type);
        event.setDetail(detail);
        event.setOutcome(outcome);
        event.setSource("SERVER");
        event.setCardUid(uid);
        event.setScanId(scanId);
        event.setOccurredAt(Instant.now());
        events.save(event);
    }

    @Transactional(readOnly = true)
    public List<EventView> recent(int limit) {
        if (limit < 1 || limit > 100) throw bad("Event limit must be 1-100");
        Map<UUID, String> names = new HashMap<>();
        return events.findAllByOrderByReceivedAtDescIdDesc(PageRequest.of(0, limit)).stream().map(e ->
                new EventView(e.getId(), e.getDeviceId(), names.computeIfAbsent(e.getDeviceId(),
                        id -> devices.findById(id).map(HardwareDevice::getName).orElse("Deleted Pi")),
                        e.getType(), e.getDetail(), e.getOutcome(), e.getSource(), e.getOccurredAt(), e.getReceivedAt(),
                        e.getCardUid(), e.getScanId())).toList();
    }

    @Scheduled(fixedDelay = 10000)
    public void monitor() {
        for (HardwareDevice d : devices.findDisconnected(Instant.now().minusSeconds(45))) {
            d.setConnected(false);
            audit(d, "CONNECTION_LOST", "No contact for at least 45 seconds; power, network or service may be unavailable",
                    "ERROR", null, null);
        }
    }
}
