package com.makerspace.backend.services;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.controller.dto.CancellationRequestDTO;
import com.makerspace.backend.model.*;
import com.makerspace.backend.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
public class CancellationRequestService {

    @Autowired private CancellationRequestRepository cancellationRequestRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PaymentRecordRepository paymentRecordRepository;
    @Autowired private UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Submit (user-facing)
    // -------------------------------------------------------------------------

    @Transactional
    public CancellationRequestDTO submit(Long reservationId, String reason, UserPrincipal principal) {
        EquipmentReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));

        boolean isOwner = reservation.getUserId().equals(principal.userId());
        boolean isAdmin  = hasPermission(principal, Permission.MANAGE_RESERVATIONS);
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        ReservationStatus status = reservation.getStatus();
        if (status != ReservationStatus.ACTIVE
                && status != ReservationStatus.TRAINING_PENDING
                && status != ReservationStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cancellation requests can only be submitted for active or completed reservations");
        }

        if (status == ReservationStatus.COMPLETED) {
            ZonedDateTime window = reservation.getEndTime().plusHours(48);
            if (ZonedDateTime.now().isAfter(window)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "The 48-hour refund window for this reservation has expired");
            }
        }

        cancellationRequestRepository.findByReservationId(reservationId).ifPresent(existing -> {
            if (existing.getStatus() == CancellationRequestStatus.PENDING
                    || existing.getStatus() == CancellationRequestStatus.APPROVED_WITH_REFUND) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A cancellation request already exists for this reservation");
            }
        });

        User requester = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        CancellationRequest cr = new CancellationRequest();
        cr.setReservation(reservation);
        cr.setRequestedBy(requester);
        cr.setReason(reason);
        cr.setStatus(CancellationRequestStatus.PENDING);
        cr.setRefundEligibleUntil(reservation.getEndTime().plusHours(48));

        CancellationRequest saved = cancellationRequestRepository.save(cr);
        log.info("Cancellation request submitted: id={} reservationId={} userId={}",
                saved.getId(), reservationId, principal.userId());

        PaymentRecord pr = paymentRecordRepository.findByReservationId(reservationId).orElse(null);
        return CancellationRequestDTO.from(saved, pr);
    }

    // -------------------------------------------------------------------------
    // Admin list / count
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CancellationRequestDTO> listAll() {
        return cancellationRequestRepository.findAllByOrderByRequestedAtDesc().stream()
                .map(cr -> {
                    PaymentRecord pr = paymentRecordRepository
                            .findByReservationId(cr.getReservation().getId()).orElse(null);
                    return CancellationRequestDTO.from(cr, pr);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CancellationRequestDTO> listByStatus(CancellationRequestStatus status) {
        return cancellationRequestRepository.findByStatusOrderByRequestedAtAsc(status).stream()
                .map(cr -> {
                    PaymentRecord pr = paymentRecordRepository
                            .findByReservationId(cr.getReservation().getId()).orElse(null);
                    return CancellationRequestDTO.from(cr, pr);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return cancellationRequestRepository.countByStatus(CancellationRequestStatus.PENDING);
    }

    // -------------------------------------------------------------------------
    // Admin resolve
    // -------------------------------------------------------------------------

    @Transactional
    public CancellationRequestDTO adminResolve(Long requestId, String decision, String adminNote,
                                               UserPrincipal principal) {
        CancellationRequest cr = cancellationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cancellation request not found"));

        if (cr.getStatus() != CancellationRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only PENDING requests can be resolved");
        }

        EquipmentReservation reservation = cr.getReservation();

        switch (decision) {
            case "APPROVE_WITH_REFUND" -> {
                if (cr.getRefundEligibleUntil().isBefore(ZonedDateTime.now())) {
                    log.warn("Refund window expired for cancellation request {} but admin proceeding anyway",
                            requestId);
                }
                cancelIfActive(reservation);
                cr.setStatus(CancellationRequestStatus.APPROVED_WITH_REFUND);
            }
            case "APPROVE_NO_REFUND" -> {
                cancelIfActive(reservation);
                cr.setStatus(CancellationRequestStatus.APPROVED_NO_REFUND);
            }
            case "REJECT" -> {
                cr.setStatus(CancellationRequestStatus.REJECTED);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown decision: " + decision + ". Must be APPROVE_WITH_REFUND, APPROVE_NO_REFUND, or REJECT");
        }

        cr.setAdminNote(adminNote);
        cr.setResolvedAt(ZonedDateTime.now());
        CancellationRequest saved = cancellationRequestRepository.save(cr);

        log.info("Cancellation request {} resolved as {} by userId={}",
                requestId, decision, principal.userId());

        PaymentRecord pr = paymentRecordRepository.findByReservationId(reservation.getId()).orElse(null);
        return CancellationRequestDTO.from(saved, pr);
    }

    // -------------------------------------------------------------------------
    // Stripe webhook callback
    // -------------------------------------------------------------------------

    @Transactional
    public void handleStripeRefundConfirmed(String stripeChargeId) {
        PaymentRecord pr = paymentRecordRepository.findByStripeChargeId(stripeChargeId).orElse(null);
        if (pr == null) {
            log.warn("charge.refunded: no PaymentRecord found for chargeId={}", stripeChargeId);
            return;
        }

        pr.setStatus(PaymentStatus.REFUNDED);
        paymentRecordRepository.save(pr);
        log.info("PaymentRecord {} marked REFUNDED via charge.refunded for chargeId={}", pr.getId(), stripeChargeId);

        if (pr.getReservationId() == null) return;

        cancellationRequestRepository.findByReservationId(pr.getReservationId()).ifPresent(cr -> {
            if (cr.getStatus() == CancellationRequestStatus.APPROVED_WITH_REFUND) {
                cr.setStatus(CancellationRequestStatus.COMPLETED);
                cr.setResolvedAt(ZonedDateTime.now());
                cancellationRequestRepository.save(cr);
                log.info("CancellationRequest {} marked COMPLETED after Stripe refund confirmed", cr.getId());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void cancelIfActive(EquipmentReservation reservation) {
        if (reservation.getStatus() == ReservationStatus.ACTIVE
                || reservation.getStatus() == ReservationStatus.TRAINING_PENDING) {
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservation.setCancelledAt(ZonedDateTime.now());
            reservationRepository.save(reservation);
        }
    }

    private boolean hasPermission(UserPrincipal principal, String permission) {
        return principal.authorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permission));
    }
}
