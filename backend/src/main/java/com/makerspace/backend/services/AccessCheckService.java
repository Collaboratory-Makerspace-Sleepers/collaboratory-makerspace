package com.makerspace.backend.services;

import com.makerspace.backend.controller.dto.AccessCheckResponse;
import com.makerspace.backend.model.EquipmentReservation;
import com.makerspace.backend.model.ReservationStatus;
import com.makerspace.backend.repository.ReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;

@Slf4j
@Service
public class AccessCheckService {

    /** How many minutes before a reservation's start time the user may enter. */
    private static final int EARLY_ENTRY_MINUTES = 30;

    @Autowired private ReservationRepository reservationRepository;
    @Autowired private MembershipService membershipService;

    /**
     * Determines whether a user may access a specific piece of equipment right now.
     *
     * Grants access when ALL of the following hold:
     *   1. An ACTIVE reservation for this user + equipment exists whose window contains
     *      the current time, allowing entry up to {@value EARLY_ENTRY_MINUTES} min early.
     *   2. The user still has a valid membership or studio assignment.
     *
     * Returns a precise denial reason to display on the kiosk so staff can act on it.
     */
    @Transactional(readOnly = true)
    public AccessCheckResponse check(Long userId, Long equipmentId) {
        ZonedDateTime now       = ZonedDateTime.now();
        ZonedDateTime windowEnd = now.plusMinutes(EARLY_ENTRY_MINUTES);

        // 1. Look for an ACTIVE reservation inside the access window.
        List<EquipmentReservation> activeReservations = reservationRepository
                .findByUserEquipmentAndStatusesInWindow(
                        userId, equipmentId,
                        EnumSet.of(ReservationStatus.ACTIVE),
                        now, windowEnd);

        if (activeReservations.isEmpty()) {
            // Check for TRAINING_PENDING so we can give a more helpful message.
            List<EquipmentReservation> pendingReservations = reservationRepository
                    .findByUserEquipmentAndStatusesInWindow(
                            userId, equipmentId,
                            EnumSet.of(ReservationStatus.TRAINING_PENDING),
                            now, windowEnd);

            if (!pendingReservations.isEmpty()) {
                log.info("Access denied — training incomplete: userId={} equipmentId={}", userId, equipmentId);
                return AccessCheckResponse.denied(
                        "Training not completed — please watch the training video and confirm attestation in the app");
            }

            log.info("Access denied — no reservation: userId={} equipmentId={}", userId, equipmentId);
            return AccessCheckResponse.denied(
                    "No active reservation found within the next " + EARLY_ENTRY_MINUTES + " minutes");
        }

        // 2. Verify membership is still valid (may have lapsed since booking).
        boolean hasMembership = membershipService.hasActiveMembership(userId)
                || membershipService.hasMachineRateWaiver(userId);

        if (!hasMembership) {
            log.info("Access denied — membership lapsed: userId={} equipmentId={}", userId, equipmentId);
            return AccessCheckResponse.denied("Membership is no longer active — please renew before entering");
        }

        EquipmentReservation reservation = activeReservations.get(0);
        log.info("Access granted: userId={} equipmentId={} reservationId={}", userId, equipmentId, reservation.getId());
        return AccessCheckResponse.granted(reservation.getId());
    }
}
