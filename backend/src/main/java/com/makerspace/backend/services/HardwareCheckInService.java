package com.makerspace.backend.services;

import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.HardwareCheckIn;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.repository.HardwareCheckInRepository;
import com.makerspace.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class HardwareCheckInService {

    private static final int MAX_SIGNATURE_CHARS = 700_000;

    private static final List<HardwareCheckIn.CheckInStatus> WAIVER_STATUSES = List.of(
            HardwareCheckIn.CheckInStatus.NEEDS_WAIVER,
            HardwareCheckIn.CheckInStatus.WAIVER_SIGNED,
            HardwareCheckIn.CheckInStatus.WAIVER_APPROVED,
            HardwareCheckIn.CheckInStatus.DENIED);

    @Autowired
    private HardwareCheckInRepository checkInRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    /**
     * Handle hardware check-in from Raspberry Pi device.
     * Checks user permissions, waiver status, and equipment access.
     */
    @Transactional
    public HardwareCheckIn checkIn(Long userId, Long equipmentId, String deviceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));

        HardwareCheckIn checkIn = new HardwareCheckIn(user, equipment, deviceId);

        // Check if equipment requires waiver
        if (equipment.isRequiresWaiver()) {
            // Check if user has approved waiver for this equipment
            List<HardwareCheckIn> approvedCheckIns = checkInRepository
                    .findByStatusAndWaiverApprovedByAdminIsTrueOrderByCheckinTimeDesc(HardwareCheckIn.CheckInStatus.WAIVER_APPROVED);

            boolean hasValidWaiver = approvedCheckIns.stream()
                    .anyMatch(c -> c.getUser().getId().equals(userId) && 
                                  c.getEquipment().getId().equals(equipmentId));

            if (!hasValidWaiver) {
                checkIn.setStatus(HardwareCheckIn.CheckInStatus.NEEDS_WAIVER);
                checkIn.setAccessGranted(false);
                log.info("Check-in denied for user {} on equipment {} - waiver required", userId, equipmentId);
                return checkInRepository.save(checkIn);
            }
        }

        // Check user permissions via user roles
        boolean hasAccess = user.getRoles().stream()
                .anyMatch(role -> role.getCode().equals("ADMIN") || role.getCode().equals("MEMBER"));

        checkIn.setStatus(hasAccess ? HardwareCheckIn.CheckInStatus.SUCCESS : HardwareCheckIn.CheckInStatus.FAILED);
        checkIn.setAccessGranted(hasAccess);

        if (!hasAccess) {
            checkIn.setErrorMessage("User does not have permission to access this equipment");
            log.warn("Check-in denied for user {} - insufficient permissions", userId);
        } else {
            log.info("Check-in successful for user {} on equipment {}", userId, equipmentId);
        }

        return checkInRepository.save(checkIn);
    }

    /**
     * Get all pending hardware check-ins that require waiver approval
     */
    @Transactional(readOnly = true)
    public Page<HardwareCheckIn> getPendingWaivers(Pageable pageable) {
        return checkInRepository.findPendingWaivers(pageable);
    }

    /**
     * Get recent check-ins
     */
    @Transactional(readOnly = true)
    public Page<HardwareCheckIn> getRecentCheckIns(Pageable pageable) {
        return checkInRepository.findAll(pageable);
    }

    /**
     * Get check-ins for a specific user
     */
    @Transactional(readOnly = true)
    public Page<HardwareCheckIn> getUserCheckIns(Long userId, Pageable pageable) {
        return checkInRepository.findByUserIdOrderByCheckinTimeDesc(userId, pageable);
    }

    /**
     * Approve waiver for a hardware check-in
     */
    @Transactional
    public HardwareCheckIn approveWaiver(Long checkInId, Long approverId) {
        HardwareCheckIn checkIn = checkInRepository.findById(checkInId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check-in not found"));

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Approver not found"));

        if (checkIn.getWaiverSigned() == null || !checkIn.getWaiverSigned()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waiver not yet signed");
        }

        checkIn.setWaiverApprovedByAdmin(true);
        checkIn.setApprovedByAdmin(approver);
        checkIn.setApprovalTime(Instant.now());
        checkIn.setStatus(HardwareCheckIn.CheckInStatus.WAIVER_APPROVED);
        checkIn.setAccessGranted(true);

        log.info("Waiver approved for check-in {} by user {}", checkInId, approver.getId());
        return checkInRepository.save(checkIn);
    }

    /**
     * Deny access for a hardware check-in
     */
    @Transactional
    public HardwareCheckIn denyAccess(Long checkInId, String reason) {
        HardwareCheckIn checkIn = checkInRepository.findById(checkInId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check-in not found"));

        checkIn.setStatus(HardwareCheckIn.CheckInStatus.DENIED);
        checkIn.setAccessGranted(false);
        checkIn.setErrorMessage(reason);

        log.info("Access denied for check-in {} - reason: {}", checkInId, reason);
        return checkInRepository.save(checkIn);
    }

    /**
     * Mark waiver as signed with rabbit signature
     */
    @Transactional
    public HardwareCheckIn signWaiver(Long checkInId, String signatureData, Long requesterId, boolean isStaff) {
        HardwareCheckIn checkIn = checkInRepository.findById(checkInId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check-in not found"));

        if (!isStaff && !checkIn.getUser().getId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only sign your own waiver");
        }
        if (checkIn.getStatus() == HardwareCheckIn.CheckInStatus.DENIED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Access was denied for this check-in");
        }
        if (!signatureData.startsWith("data:image/png;base64,") || signatureData.length() > MAX_SIGNATURE_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid signature image");
        }
        if (Boolean.TRUE.equals(checkIn.getWaiverApprovedByAdmin())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Waiver already approved");
        }

        checkIn.setSignatureData(signatureData);
        checkIn.setWaiverSigned(true);
        checkIn.setStatus(HardwareCheckIn.CheckInStatus.WAIVER_SIGNED);

        log.info("Waiver signed for check-in {} with rabbit signature", checkInId);
        return checkInRepository.save(checkIn);
    }

    /**
     * The user's latest waiver request for a piece of equipment (null if none), which
     * lets the reservation waiver page resume at the waiting or approved step.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<HardwareCheckIn> getLatestForEquipment(Long userId, Long equipmentId) {
        return checkInRepository.findFirstByUserIdAndEquipmentIdAndStatusInOrderByCheckinTimeDesc(
                userId, equipmentId, WAIVER_STATUSES);
    }

    /** The user's most recent waiver request per equipment from the last 7 days, newest first. */
    @Transactional(readOnly = true)
    public List<HardwareCheckIn> getMyWaiverRequests(Long userId) {
        java.util.Map<Long, HardwareCheckIn> latest = new java.util.LinkedHashMap<>();
        for (HardwareCheckIn c : checkInRepository
                .findByUserIdAndStatusInAndCheckinTimeAfterOrderByCheckinTimeDesc(
                        userId, WAIVER_STATUSES,
                        Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS))) {
            latest.putIfAbsent(c.getEquipment().getId(), c);
        }
        return new java.util.ArrayList<>(latest.values());
    }

    /**
     * Records a waiver signed from the reservation flow so it shows up for staff on the
     * hardware dashboard. Reuses an open or approved request instead of duplicating it.
     */
    @Transactional
    public HardwareCheckIn submitWaiver(Long userId, Long equipmentId, String signatureData) {
        HardwareCheckIn existing = checkInRepository
                .findFirstByUserIdAndEquipmentIdAndStatusInOrderByCheckinTimeDesc(
                        userId, equipmentId,
                        List.of(HardwareCheckIn.CheckInStatus.NEEDS_WAIVER,
                                HardwareCheckIn.CheckInStatus.WAIVER_SIGNED,
                                HardwareCheckIn.CheckInStatus.WAIVER_APPROVED))
                .orElse(null);

        if (existing != null && existing.getStatus() != HardwareCheckIn.CheckInStatus.NEEDS_WAIVER) {
            return existing;
        }

        HardwareCheckIn target = existing;
        if (target == null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
            Equipment equipment = equipmentRepository.findById(equipmentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));
            target = new HardwareCheckIn(user, equipment, "web");
            target.setStatus(HardwareCheckIn.CheckInStatus.NEEDS_WAIVER);
            target.setAccessGranted(false);
            target = checkInRepository.save(target);
        }
        return signWaiver(target.getId(), signatureData, userId, false);
    }

    @Transactional(readOnly = true)
    public HardwareCheckIn getForUser(Long checkInId, Long requesterId, boolean isStaff) {
        HardwareCheckIn checkIn = checkInRepository.findById(checkInId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check-in not found"));
        if (!isStaff && !checkIn.getUser().getId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your check-in");
        }
        return checkIn;
    }

    @Transactional(readOnly = true)
    public String getSignature(Long checkInId) {
        HardwareCheckIn checkIn = checkInRepository.findById(checkInId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check-in not found"));
        if (checkIn.getSignatureData() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No signature on file");
        }
        return checkIn.getSignatureData();
    }
}
