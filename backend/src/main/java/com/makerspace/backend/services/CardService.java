package com.makerspace.backend.services;

import com.makerspace.backend.model.AppRole;
import com.makerspace.backend.model.CardScan;
import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.EquipmentStatus;
import com.makerspace.backend.model.HardwareCheckIn;
import com.makerspace.backend.model.MemberCard;
import com.makerspace.backend.model.UnregisteredCard;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.CardScanRepository;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.repository.MemberCardRepository;
import com.makerspace.backend.repository.TrainingTaskRepository;
import com.makerspace.backend.repository.UnregisteredCardRepository;
import com.makerspace.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Card-UID access. A card only stores which member it belongs to; every scan re-reads the
 * member's current membership, training and permissions, so staff can change any of that
 * later without the card being scanned again.
 */
@Slf4j
@Service
public class CardService {

    public record ScanResult(boolean approved, String message, String memberName, Long userId, Long scanId) {}

    @Autowired private MemberCardRepository cardRepository;
    @Autowired private CardScanRepository scanRepository;
    @Autowired private UnregisteredCardRepository unregisteredCardRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private TrainingTaskRepository trainingTaskRepository;
    @Autowired private HardwareCheckInService checkInService;
    @Autowired private EmailService emailService;

    public static String normalizeUid(String raw) {
        if (raw == null) return "";
        String uid = raw.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
        return uid.length() >= 4 && uid.length() <= 32 ? uid : "";
    }

    public static String displayName(User u) {
        if (u.getProfile() != null) {
            String n = ((u.getProfile().getFirstName() == null ? "" : u.getProfile().getFirstName()) + " "
                    + (u.getProfile().getLastName() == null ? "" : u.getProfile().getLastName())).trim();
            if (!n.isEmpty()) return n;
        }
        return u.getEmail();
    }

    @Transactional
    public ScanResult scan(String rawUid, String deviceId, Long equipmentId) {
        String uid = normalizeUid(rawUid);
        if (uid.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cardUid must be 4-32 hex characters");
        }

        MemberCard card = cardRepository.findByCardUid(uid).orElse(null);
        if (card == null) {
            // Track unregistered card for admin dashboard
            UnregisteredCard unregistered = unregisteredCardRepository.findByCardUid(uid).orElse(null);
            if (unregistered == null) {
                unregistered = new UnregisteredCard();
                unregistered.setCardUid(uid);
                unregistered.setFirstScannedAt(Instant.now());
                unregistered.setLastScannedAt(Instant.now());
                unregistered.setDeviceId(deviceId);
                unregistered.setScanCount(1);
                unregistered.setStatus("PENDING");
            } else {
                unregistered.setLastScannedAt(Instant.now());
                unregistered.setScanCount((unregistered.getScanCount() != null ? unregistered.getScanCount() : 0) + 1);
            }
            unregisteredCardRepository.save(unregistered);
            return record(uid, null, null, deviceId, false, "Card not registered - awaiting admin assignment");
        }
        card.setLastSeenAt(Instant.now());
        User user = card.getUser();

        if (!card.isActive()) {
            return record(uid, user, null, deviceId, false, "Card is deactivated");
        }
        if (user.getAccountStatus() != com.makerspace.backend.model.AccountStatus.ACTIVE) {
            return record(uid, user, null, deviceId, false, "Account is not active");
        }

        Equipment equipment = null;
        if (equipmentId != null) {
            equipment = equipmentRepository.findById(equipmentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));
            if (equipment.getStatus() == EquipmentStatus.MAINTENANCE) {
                return record(uid, user, equipment, deviceId, false, "Equipment is under maintenance");
            }
        }

        if (!checkInService.hasFacilityAccess(user)) {
            return record(uid, user, equipment, deviceId, false, "No active membership or day pass");
        }

        if (equipment != null) {
            boolean staff = user.effectivePermissions().contains("MANAGE_EQUIPMENT");
            if (equipment.isTrainingRequired() && !staff) {
                boolean trained = trainingTaskRepository
                        .findByUserIdAndEquipmentId(user.getId(), equipment.getId())
                        .map(t -> t.getVideoCompletedAt() != null).orElse(false);
                if (!trained) {
                    return record(uid, user, equipment, deviceId, false, "Training not completed");
                }
            }
            // Reuses the waiver flow so a missing waiver shows up for staff to approve.
            HardwareCheckIn checkIn = checkInService.checkIn(user.getId(), equipment.getId(),
                    deviceId == null ? "card" : deviceId);
            if (!Boolean.TRUE.equals(checkIn.getAccessGranted())) {
                String reason = checkIn.getStatus() == HardwareCheckIn.CheckInStatus.NEEDS_WAIVER
                        ? "Waiver not approved" : "Access denied";
                return record(uid, user, equipment, deviceId, false, reason);
            }
        }
        return record(uid, user, equipment, deviceId, true, "Access granted");
    }

    private ScanResult record(String uid, User user, Equipment equipment, String deviceId,
                              boolean approved, String reason) {
        CardScan scan = new CardScan();
        scan.setCardUid(uid);
        scan.setUser(user);
        scan.setEquipment(equipment);
        scan.setDeviceId(deviceId);
        scan.setApproved(approved);
        scan.setReason(reason);
        scan = scanRepository.save(scan);
        log.info("Card scan uid={} device={} approved={} reason={}", uid, deviceId, approved, reason);
        return new ScanResult(approved, reason, user == null ? null : displayName(user),
                user == null ? null : user.getId(), scan.getId());
    }

    @Transactional(readOnly = true)
    public List<MemberCard> listCards() {
        return cardRepository.findAllByOrderByAssignedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<CardScan> recentScans(int limit) {
        return scanRepository.findAllByOrderByScannedAtDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
    }

    @Transactional
    public MemberCard assign(String rawUid, Long userId, String label, Long staffId) {
        String uid = normalizeUid(rawUid);
        if (uid.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card UID must be 4-32 hex characters");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        MemberCard card = cardRepository.findByCardUid(uid).orElse(null);
        if (card != null && card.isActive() && !card.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Card is already assigned to " + displayName(card.getUser()));
        }
        if (card == null) card = new MemberCard();
        card.setCardUid(uid);
        card.setUser(user);
        card.setLabel(label);
        card.setActive(true);
        card.setAssignedAt(Instant.now());
        card.setAssignedBy(userRepository.findById(staffId).orElse(null));
        return cardRepository.save(card);
    }

    /** Reassign to another member and/or toggle active, without rescanning the card. */
    @Transactional
    public MemberCard update(Long id, Long userId, Boolean active, String label) {
        MemberCard card = cardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
        if (userId != null) {
            card.setUser(userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found")));
        }
        if (active != null) card.setActive(active);
        if (label != null) card.setLabel(label.isBlank() ? null : label.trim());
        return cardRepository.save(card);
    }

    @Transactional
    public void delete(Long id) {
        if (!cardRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
        }
        cardRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<User> searchMembers(String q) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return userRepository.findAll().stream()
                .filter(u -> u.getDeletedAt() == null)
                .filter(u -> needle.isEmpty()
                        || displayName(u).toLowerCase(Locale.ROOT).contains(needle)
                        || u.getEmail().toLowerCase(Locale.ROOT).contains(needle))
                .limit(30).toList();
    }

    // Unregistered card management
    @Transactional(readOnly = true)
    public List<UnregisteredCard> listUnregisteredCards() {
        return unregisteredCardRepository.findAll().stream()
                .filter(c -> "PENDING".equals(c.getStatus()) || "ASSIGNED".equals(c.getStatus()))
                .sorted((a, b) -> b.getLastScannedAt().compareTo(a.getLastScannedAt()))
                .toList();
    }

    @Transactional
    public void deleteUnregisteredCard(Long cardId) {
        UnregisteredCard card = unregisteredCardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
        log.info("Deleting unregistered card {} (UID: {})", cardId, card.getCardUid());
        unregisteredCardRepository.deleteById(cardId);
    }

    @Transactional
    public UnregisteredCard assignToEmail(Long unregisteredCardId, String email) {
        UnregisteredCard card = unregisteredCardRepository.findById(unregisteredCardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
        card.setAssignedToEmail(email);
        card.setAssignedAt(Instant.now());
        card.setStatus("ASSIGNED");
        return unregisteredCardRepository.save(card);
    }

    @Transactional
    public UnregisteredCard assignRole(Long unregisteredCardId, String role) {
        UnregisteredCard card = unregisteredCardRepository.findById(unregisteredCardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
        
        // Validate role
        if (!role.matches("(?i)(MEMBER|ADMIN|STAFF)")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Invalid role. Must be one of: MEMBER, ADMIN, STAFF");
        }
        
        card.setAssignedRole(role.toUpperCase());
        card.setRoleAssignedAt(Instant.now());
        return unregisteredCardRepository.save(card);
    }

    @Transactional
    public UnregisteredCard generateOtp(Long unregisteredCardId) {
        UnregisteredCard card = unregisteredCardRepository.findById(unregisteredCardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
        if (card.getAssignedToEmail() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card must be assigned to an email first");
        }
        // Generate OTP
        String otp = String.format("%06d", (int)(Math.random() * 1000000));
        card.setOtpCode(otp);
        card.setOtpGeneratedAt(Instant.now());
        card.setOtpExpiresAt(Instant.now().plusSeconds(3600)); // Valid for 1 hour
        card.setOtpVerified(false);
        return unregisteredCardRepository.save(card);
    }

    @Transactional
    public UnregisteredCard verifyOtp(String otpCode, Long userId) {
        UnregisteredCard card = unregisteredCardRepository.findByOtpCode(otpCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid OTP"));
        
        if (Instant.now().isAfter(card.getOtpExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP has expired");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        card.setOtpVerified(true);
        card.setVerifiedAt(Instant.now());
        card.setStatus("VERIFIED");
        return unregisteredCardRepository.save(card);
    }

    @Transactional
    public UnregisteredCard verifyOtp(String otpCode, String email) {
        UnregisteredCard card = unregisteredCardRepository.findByOtpCode(otpCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid OTP"));
        
        // Verify OTP belongs to this email
        if (!email.equalsIgnoreCase(card.getAssignedToEmail())) {
            log.warn("OTP code does not match assigned email. Code: {}, Email: {}", otpCode, email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OTP does not match email");
        }
        
        // Check if OTP has expired
        if (Instant.now().isAfter(card.getOtpExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP has expired");
        }
        
        // Mark OTP as verified
        card.setOtpVerified(true);
        card.setVerifiedAt(Instant.now());
        card.setStatus("VERIFIED");
        return unregisteredCardRepository.save(card);
    }

    public java.util.Optional<UnregisteredCard> getUnregisteredCardByUid(String cardUid) {
        String uid = normalizeUid(cardUid);
        if (uid.isEmpty()) {
            return java.util.Optional.empty();
        }
        return unregisteredCardRepository.findByCardUid(uid);
    }

    @Transactional
    public MemberCard activateCardAfterOtp(String cardUid, Long userId) {
        String uid = normalizeUid(cardUid);
        if (uid.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card UID must be 4-32 hex characters");
        }

        UnregisteredCard unregistered = unregisteredCardRepository.findByCardUid(uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));

        if (!unregistered.isOtpVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP not verified for this card");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Assign role if one was specified
        if (unregistered.getAssignedRole() != null && !unregistered.getAssignedRole().isEmpty()) {
            String roleCode = unregistered.getAssignedRole().toUpperCase();
            // Clear existing roles
            user.getRoles().clear();
            // Assign the new role by code
            AppRole appRole = new AppRole();
            appRole.setCode(roleCode);
            user.getRoles().add(appRole);
            userRepository.save(user);
        }

        // Create the member card
        MemberCard card = new MemberCard();
        card.setCardUid(uid);
        card.setUser(user);
        card.setLabel(null);
        card.setActive(true);
        card.setAssignedAt(Instant.now());
        card.setAssignedBy(null);
        cardRepository.save(card);

        // Mark unregistered card as activated
        unregistered.setStatus("ACTIVATED");
        unregisteredCardRepository.save(unregistered);

        return card;
    }

    /**
     * Generate OTP code and send it to the user's email.
     * Called by the user after their card is assigned by admin.
     */
    @Transactional
    public void generateAndSendOtp(UnregisteredCard card) {
        if (card.getAssignedToEmail() == null || card.getAssignedToEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card must be assigned to an email first");
        }

        // Generate OTP
        String otp = String.format("%06d", (int)(Math.random() * 1000000));
        card.setOtpCode(otp);
        card.setOtpGeneratedAt(Instant.now());
        card.setOtpExpiresAt(Instant.now().plusSeconds(3600)); // Valid for 1 hour
        card.setOtpVerified(false);
        card.setStatus("VERIFIED"); // Update status to VERIFIED after OTP is sent

        // Save the card with OTP
        unregisteredCardRepository.save(card);

        // Send OTP email
        try {
            emailService.sendCardActivationOtp(card.getAssignedToEmail(), otp, card.getCardUid());
            log.info("OTP sent to {} for card {}", card.getAssignedToEmail(), card.getCardUid());
        } catch (Exception e) {
            log.error("Failed to send OTP email: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send OTP email");
        }
    }

}
