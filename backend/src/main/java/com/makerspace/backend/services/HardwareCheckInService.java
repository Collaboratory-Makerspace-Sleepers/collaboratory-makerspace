package com.makerspace.backend.services;

import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.HardwareCheckIn;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.repository.HardwareCheckInRepository;
import com.makerspace.backend.repository.UserRepository;
import com.makerspace.backend.repository.WaiverDocumentRepository;
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

    private static final List<HardwareCheckIn.CheckInStatus> WAIVER_STATUSES = List.of(
            HardwareCheckIn.CheckInStatus.NEEDS_WAIVER,
            HardwareCheckIn.CheckInStatus.WAIVER_SIGNED,
            HardwareCheckIn.CheckInStatus.WAIVER_APPROVED,
            HardwareCheckIn.CheckInStatus.DENIED);

    @Autowired private HardwareCheckInRepository checkInRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipService membershipService;
    @Autowired private ReservationService reservationService;
    @Autowired private WaiverPdfService waiverPdfService;
    @Autowired private EmailService emailService;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private WaiverDocumentRepository waiverDocumentRepository;

    /**
     * Handle hardware check-in from Raspberry Pi device.
     */
    @Transactional
    public HardwareCheckIn checkIn(Long userId, Long equipmentId, String deviceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));

        HardwareCheckIn checkIn = new HardwareCheckIn(user, equipment, deviceId);

        if (equipment.isRequiresWaiver()) {
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

        boolean hasAccess = hasFacilityAccess(user);

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

    /** Staff, legacy ADMIN/MEMBER roles, an active membership, or a Day Pass in the last 24h. */
    public boolean hasFacilityAccess(User user) {
        boolean roleOk = user.getRoles().stream()
                .anyMatch(r -> r.getCode().equals("ADMIN") || r.getCode().equals("MEMBER"))
                || user.effectivePermissions().contains("MANAGE_EQUIPMENT");
        return roleOk || membershipService.hasActiveMembership(user.getId())
                || reservationService.hasRecentDayPass(user.getId());
    }

    @Transactional(readOnly = true)
    public Page<HardwareCheckIn> getPendingWaivers(Pageable pageable) {
        return checkInRepository.findPendingWaivers(pageable);
    }

    @Transactional(readOnly = true)
    public Page<HardwareCheckIn> getRecentCheckIns(Pageable pageable) {
        return checkInRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<HardwareCheckIn> getUserCheckIns(Long userId, Pageable pageable) {
        return checkInRepository.findByUserIdOrderByCheckinTimeDesc(userId, pageable);
    }

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

    @Transactional
    public HardwareCheckIn signWaiver(Long checkInId, String signedNameInput, Long requesterId, boolean isStaff) {
        HardwareCheckIn checkIn = checkInRepository.findById(checkInId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check-in not found"));

        if (!isStaff && !checkIn.getUser().getId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only sign your own waiver");
        }
        if (checkIn.getStatus() == HardwareCheckIn.CheckInStatus.DENIED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Access was denied for this check-in");
        }
        String signedName = signedNameInput == null ? "" : signedNameInput.trim().replaceAll("\\s+", " ");
        if (signedName.length() < 2 || signedName.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your full name to sign");
        }
        if (Boolean.TRUE.equals(checkIn.getWaiverApprovedByAdmin())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Waiver already approved");
        }

        checkIn.setSignedName(signedName);
        checkIn.setSignedAt(Instant.now());
        checkIn.setWaiverSigned(true);
        checkIn.setStatus(HardwareCheckIn.CheckInStatus.WAIVER_SIGNED);

        log.info("Waiver signed for check-in {}", checkInId);
        HardwareCheckIn saved = checkInRepository.save(checkIn);
        emailSignedWaiver(saved);
        return saved;
    }

    private void emailSignedWaiver(HardwareCheckIn c) {
        User user = c.getUser();
        String email = user.getEmail();
        String memberName = user.getProfile() != null
                ? (user.getProfile().getFirstName() + " " + user.getProfile().getLastName()).trim()
                : email;
        String equipment = c.getEquipment().getName();
        String signedName = c.getSignedName();
        java.time.ZonedDateTime when = c.getSignedAt().atZone(java.time.ZoneId.systemDefault());
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                byte[] pdf = waiverPdfService.build(memberName, email, equipment, signedName, when);
                emailService.sendWaiverSigned(email, memberName, equipment, signedName, pdf);
            } catch (Exception e) {
                log.warn("Could not email signed waiver for check-in {}: {}", c.getId(), e.getMessage());
            }
        });
    }

    @Transactional(readOnly = true)
    public java.util.Optional<HardwareCheckIn> getLatestForEquipment(
            Long userId, Long equipmentId, Instant slotStart) {
        return checkInRepository
                .findByUserIdAndEquipmentIdAndStatusInOrderByCheckinTimeDesc(userId, equipmentId, WAIVER_STATUSES)
                .stream()
                .filter(c -> java.util.Objects.equals(c.getRequestedStart(), slotStart))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<HardwareCheckIn> getMyWaiverRequests(Long userId) {
        Instant now = Instant.now();
        java.util.Map<String, HardwareCheckIn> latest = new java.util.LinkedHashMap<>();
        for (HardwareCheckIn c : checkInRepository
                .findByUserIdAndStatusInAndCheckinTimeAfterOrderByCheckinTimeDesc(
                        userId, WAIVER_STATUSES, now.minus(7, java.time.temporal.ChronoUnit.DAYS))) {
            if (c.getRequestedEnd() != null && c.getRequestedEnd().isBefore(now)) continue;
            latest.putIfAbsent(c.getEquipment().getId() + "|" + c.getRequestedStart(), c);
        }
        return new java.util.ArrayList<>(latest.values());
    }

    @Transactional
    public HardwareCheckIn submitWaiver(Long userId, Long equipmentId, Instant slotStart,
                                        Instant slotEnd, String signedName) {
        HardwareCheckIn existing = getLatestForEquipment(userId, equipmentId, slotStart)
                .filter(c -> c.getStatus() != HardwareCheckIn.CheckInStatus.DENIED)
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
            target.setRequestedStart(slotStart);
            target.setRequestedEnd(slotEnd);
            target = checkInRepository.save(target);
        }
        if (signedName == null || signedName.isBlank()) {
            signedName = CardService.displayName(target.getUser());
        }
        return signWaiver(target.getId(), signedName, userId, false);
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
    public HardwareCheckIn getSignature(Long checkInId) {
        HardwareCheckIn checkIn = checkInRepository.findById(checkInId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check-in not found"));
        if (checkIn.getSignedName() == null && checkIn.getSignatureData() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No signature on file");
        }
        return checkIn;
    }

    private static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;

    @Transactional
    public void attachSignedDocument(Long checkInId, org.springframework.web.multipart.MultipartFile file, Long staffId) {
        checkInRepository.findById(checkInId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check-in not found"));
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a PDF to upload");
        }
        if (file.getSize() > MAX_DOCUMENT_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The PDF must be 10 MB or smaller");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the file");
        }
        boolean pdf = data.length > 4 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F';
        if (!pdf) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files are accepted");
        }
        com.makerspace.backend.model.WaiverDocument doc = new com.makerspace.backend.model.WaiverDocument();
        doc.setCheckInId(checkInId);
        String name = file.getOriginalFilename() == null ? "waiver.pdf" : file.getOriginalFilename();
        doc.setFilename(name.replaceAll("[^A-Za-z0-9._ -]", "_"));
        doc.setContentType("application/pdf");
        doc.setData(data);
        doc.setUploadedByUserId(staffId);
        waiverDocumentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public com.makerspace.backend.model.WaiverDocument getSignedDocument(Long checkInId) {
        return waiverDocumentRepository.findById(checkInId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No signed document on file"));
    }

    @Transactional(readOnly = true)
    public boolean hasSignedDocument(Long checkInId) {
        return waiverDocumentRepository.existsByCheckInId(checkInId);
    }
}
