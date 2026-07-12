package com.makerspace.backend.services;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.model.*;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.repository.ReservationRepository;
import com.makerspace.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              EquipmentRepository equipmentRepository,
                              UserRepository userRepository) {
        this.reservationRepository = reservationRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public EquipmentReservation create(Long userId, Long equipmentId,
                                       ZonedDateTime startTime, ZonedDateTime endTime) {
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

        List<EquipmentReservation> conflicts = reservationRepository.findOverlapping(
                equipmentId, startTime, endTime, ReservationStatus.ACTIVE, null);
        if (!conflicts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Equipment is already reserved during that time window");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        EquipmentReservation reservation = new EquipmentReservation();
        reservation.setUser(user);
        reservation.setEquipment(equipment);
        reservation.setStartTime(startTime);
        reservation.setEndTime(endTime);

        EquipmentReservation saved = reservationRepository.save(reservation);
        log.info("Reservation created: id={} user={} equipment={} [{} - {}]",
                saved.getId(), userId, equipmentId, startTime, endTime);
        return saved;
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
        if (!isStaffOrAdmin(principal) && !reservation.getUserId().equals(principal.userId())) {
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

        if (!isStaffOrAdmin(principal) && !reservation.getUserId().equals(principal.userId())) {
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

    @Transactional(readOnly = true)
    public Page<EquipmentReservation> findAll(Pageable pageable) {
        return reservationRepository.findAllByOrderByStartTimeDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<EquipmentReservation> findByEquipment(Long equipmentId) {
        // Verify the equipment exists before returning an empty list
        if (!equipmentRepository.existsById(equipmentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found");
        }
        return reservationRepository.findByEquipmentIdOrderByStartTimeDesc(equipmentId);
    }

    private boolean isStaffOrAdmin(UserPrincipal principal) {
        return principal.authorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_STAFF") || a.getAuthority().equals("ROLE_ADMIN"));
    }
}