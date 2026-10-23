package com.makerspace.backend.services;

import com.makerspace.backend.model.CardScan;
import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.EquipmentStatus;
import com.makerspace.backend.model.HardwareCheckIn;
import com.makerspace.backend.model.MemberCard;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.CardScanRepository;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.repository.MemberCardRepository;
import com.makerspace.backend.repository.TrainingCompletionRepository;
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
    @Autowired private UserRepository userRepository;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private TrainingCompletionRepository trainingCompletionRepository;
    @Autowired private HardwareCheckInService checkInService;

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
            return record(uid, null, null, deviceId, false, "Card not registered");
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
            if (equipment.isRequiresTraining() && !staff) {
                boolean trained = trainingCompletionRepository
                        .existsByUserIdAndEquipmentId(user.getId(), equipment.getId());
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
}
