package com.makerspace.backend.services;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.EquipmentReservation;
import com.makerspace.backend.model.EquipmentStatus;
import com.makerspace.backend.model.Permission;
import com.makerspace.backend.model.ReservationStatus;
import com.makerspace.backend.model.TrainingCompletion;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.repository.ReservationRepository;
import com.makerspace.backend.repository.TrainingCompletionRepository;
import com.makerspace.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;

@Slf4j
@Service
public class ReservationService {

    @Autowired private ReservationRepository reservationRepository;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipService membershipService;
    @Autowired private TrainingCompletionRepository trainingCompletionRepository;
    @Autowired private EmailService emailService;

    @Transactional
    public EquipmentReservation create(Long userId, Long equipmentId,
                                       ZonedDateTime startTime, ZonedDateTime endTime) {

        if (!membershipService.hasActiveMembership(userId)) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "An active membership is required to reserve equipment");
        }

        if (!endTime.isAfter(startTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time");
        }

        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));

        if (equipment.getStatus() == EquipmentStatus.MAINTENANCE
                || equipment.getStatus() == EquipmentStatus.RETIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Equipment is not available for reservation (status: " + equipment.getStatus() + ")");
        }

        // If training is required and not yet done, the reservation is created in
        // TRAINING_PENDING status. The user watches the video online and calls the
        // training-attest endpoint to advance it to ACTIVE. Subsequent bookings on
        // the same equipment skip this step because a training_completion row exists.
        boolean needsTraining = equipment.isRequiresTraining()
                && !trainingCompletionRepository.existsByUserIdAndEquipmentId(userId, equipmentId);

        // Check for conflicts against both ACTIVE and TRAINING_PENDING slots.
        List<EquipmentReservation> conflicts = reservationRepository.findOverlappingByStatuses(
                equipmentId, startTime, endTime,
                EnumSet.of(ReservationStatus.ACTIVE, ReservationStatus.TRAINING_PENDING),
                null);
        if (!conflicts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Equipment is already reserved during that time window");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        boolean isMember = membershipService.hasActiveMembership(userId);
        boolean isStudioMember = membershipService.hasMachineRateWaiver(userId);
        int quotedRate = resolveRate(equipment, isMember, isStudioMember);
        long durationMinutes = Duration.between(startTime, endTime).toMinutes();
        int estimatedCost = Math.round((float) quotedRate * durationMinutes / 60);

        EquipmentReservation reservation = new EquipmentReservation();
        reservation.setUser(user);
        reservation.setEquipment(equipment);
        reservation.setStartTime(startTime);
        reservation.setEndTime(endTime);
        reservation.setStatus(needsTraining ? ReservationStatus.TRAINING_PENDING : ReservationStatus.ACTIVE);
        reservation.setQuotedRateCents(quotedRate);
        reservation.setEstimatedCostCents(estimatedCost);

        EquipmentReservation saved = reservationRepository.save(reservation);
        log.info("Reservation created: id={} user={} equipment={} status={} [{} - {}]",
                saved.getId(), userId, equipmentId, saved.getStatus(), startTime, endTime);

        try {
            String fullName = user.getProfile() != null
                    ? user.getProfile().getFirstName() + " " + user.getProfile().getLastName()
                    : user.getEmail();
            emailService.sendReservationConfirmation(
                    user.getEmail(), fullName, equipment.getName(), startTime, endTime);
        } catch (Exception e) {
            log.warn("Failed to send reservation confirmation for id={}: {}", saved.getId(), e.getMessage());
        }

        return saved;
    }

    /**
     * Confirms that the user has watched the online training video for the equipment
     * associated with this reservation. Creates a training_completion record and
     * advances the reservation from TRAINING_PENDING to ACTIVE.
     */
    @Transactional
    public EquipmentReservation trainingAttest(Long reservationId, Long userId) {
        EquipmentReservation reservation = findById(reservationId);

        if (!reservation.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        if (reservation.getStatus() != ReservationStatus.TRAINING_PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reservation is not awaiting training attestation");
        }

        Long equipmentId = reservation.getEquipmentId();

        if (!trainingCompletionRepository.existsByUserIdAndEquipmentId(userId, equipmentId)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            Equipment equipment = equipmentRepository.findById(equipmentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));

            TrainingCompletion completion = new TrainingCompletion();
            completion.setUser(user);
            completion.setEquipment(equipment);
            completion.setCompletedAt(ZonedDateTime.now());
            completion.setTrainingReservation(reservation);
            trainingCompletionRepository.save(completion);
        }

        reservation.setStatus(ReservationStatus.ACTIVE);
        log.info("Training attested: reservationId={} userId={} equipmentId={}", reservationId, userId, equipmentId);
        return reservationRepository.save(reservation);
    }

    @Transactional(readOnly = true)
    public List<EquipmentReservation> findByUser(Long userId) {
        return reservationRepository.findByUserIdOrderByStartTimeDesc(userId);
    }

    @Transactional(readOnly = true)
    public EquipmentReservation findById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
    }

    /**
     * Fetches a reservation and verifies the caller owns it.
     * STAFF and ADMIN can bypass the ownership check.
     */
    @Transactional(readOnly = true)
    public EquipmentReservation findByIdForUser(Long id, UserPrincipal principal) {
        EquipmentReservation reservation = findById(id);
        if (!hasPermission(principal, Permission.MANAGE_RESERVATIONS) && !reservation.getUserId().equals(principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return reservation;
    }

    @Transactional
    public EquipmentReservation extend(Long id, ZonedDateTime newEndTime) {
        EquipmentReservation reservation = findById(id);

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only active reservations can be extended");
        }
        if (!newEndTime.isAfter(reservation.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "New end time must be after current end time");
        }

        List<EquipmentReservation> conflicts = reservationRepository.findOverlapping(
                reservation.getEquipmentId(), reservation.getEndTime(), newEndTime,
                ReservationStatus.ACTIVE, reservation.getId());
        if (!conflicts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot extend: another reservation starts before the new end time");
        }

        reservation.setEndTime(newEndTime);
        log.info("Reservation extended: id={} newEndTime={}", id, newEndTime);
        return reservationRepository.save(reservation);
    }

    @Transactional
    public EquipmentReservation cancel(Long id, UserPrincipal principal) {
        EquipmentReservation reservation = findById(id);

        if (!hasPermission(principal, Permission.MANAGE_RESERVATIONS) && !reservation.getUserId().equals(principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot cancel another user's reservation");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reservation is already cancelled");
        }
        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot cancel a completed reservation");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(ZonedDateTime.now());
        log.info("Reservation cancelled: id={} by userId={}", id, principal.userId());
        return reservationRepository.save(reservation);
    }

    @Transactional
    public EquipmentReservation reschedule(Long id, ZonedDateTime newStartTime, ZonedDateTime newEndTime,
                                           UserPrincipal principal) {
        EquipmentReservation reservation = findById(id);

        if (!hasPermission(principal, Permission.MANAGE_RESERVATIONS)
                && !reservation.getUserId().equals(principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only active reservations can be rescheduled");
        }
        if (!newEndTime.isAfter(newStartTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "End time must be after start time");
        }

        List<EquipmentReservation> conflicts = reservationRepository.findOverlapping(
                reservation.getEquipmentId(), newStartTime, newEndTime,
                ReservationStatus.ACTIVE, reservation.getId());
        if (!conflicts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Equipment is already reserved during that time window");
        }

        reservation.setStartTime(newStartTime);
        reservation.setEndTime(newEndTime);
        log.info("Reservation rescheduled: id={} newStart={} newEnd={}", id, newStartTime, newEndTime);
        return reservationRepository.save(reservation);
    }

    @Transactional(readOnly = true)
    public Page<EquipmentReservation> findAll(Pageable pageable) {
        return reservationRepository.findAllByOrderByStartTimeDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<EquipmentReservation> findByEquipment(Long equipmentId) {
        if (!equipmentRepository.existsById(equipmentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found");
        }
        return reservationRepository.findByEquipmentIdOrderByStartTimeDesc(equipmentId);
    }

    /**
     * Returns the per-hour rate in cents that applies to this booking.
     *
     * Priority:
     *  1. Studio members (active studio_assignment) → 0 (all machines free)
     *  2. Active members booking CLASS equipment → 50% off
     *  3. Everyone else → hourly_rate_cents
     */
    private int resolveRate(Equipment equipment, boolean isMember, boolean isStudioMember) {
        if (isStudioMember) return 0;
        if (isMember && "CLASS".equals(equipment.getCategory())) return equipment.getHourlyRateCents() / 2;
        return equipment.getHourlyRateCents();
    }

    private boolean hasPermission(UserPrincipal principal, String permission) {
        return principal.authorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permission));
    }
}
