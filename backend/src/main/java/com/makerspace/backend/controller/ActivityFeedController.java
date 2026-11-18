package com.makerspace.backend.controller;

import com.makerspace.backend.model.CardScan;
import com.makerspace.backend.model.EquipmentReservation;
import com.makerspace.backend.model.HardwareCheckIn;
import com.makerspace.backend.model.PaymentRecord;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.ClassEnrollment;
import com.makerspace.backend.model.TrainingCompletion;
import com.makerspace.backend.model.ReservationStatus;
import com.makerspace.backend.model.PaymentStatus;
import com.makerspace.backend.repository.ClassEnrollmentRepository;
import com.makerspace.backend.repository.TrainingCompletionRepository;
import com.makerspace.backend.repository.CardScanRepository;
import com.makerspace.backend.repository.HardwareCheckInRepository;
import com.makerspace.backend.repository.PaymentRecordRepository;
import com.makerspace.backend.repository.ReservationRepository;
import com.makerspace.backend.services.CardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What members actually did lately: bookings, waivers, payments and card taps, newest first. */
@RestController
@RequestMapping("/api/v1/users/activity")
public class ActivityFeedController {

    @Autowired private ReservationRepository reservationRepository;
    @Autowired private HardwareCheckInRepository checkInRepository;
    @Autowired private PaymentRecordRepository paymentRecordRepository;
    @Autowired private CardScanRepository cardScanRepository;
    @Autowired private ClassEnrollmentRepository classEnrollmentRepository;
    @Autowired private TrainingCompletionRepository trainingCompletionRepository;

    public record Summary(long upcomingReservations, long pendingWaivers,
                          long classEnrollments, long successfulPayments) {}

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @Transactional(readOnly = true)
    public Summary summary() {
        ZonedDateTime now = ZonedDateTime.now();
        return new Summary(
                reservationRepository.countByStatusInAndEndTimeAfter(
                        List.of(ReservationStatus.ACTIVE, ReservationStatus.TRAINING_PENDING), now),
                checkInRepository.countByStatusIn(List.of(HardwareCheckIn.CheckInStatus.NEEDS_WAIVER,
                        HardwareCheckIn.CheckInStatus.WAIVER_SIGNED)),
                classEnrollmentRepository.countUpcoming(now),
                paymentRecordRepository.countByStatus(PaymentStatus.SUCCEEDED));
    }

    @GetMapping("/feed")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> feed(@RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        int sourceLimit = Math.min(Math.max(limit, 1), 100);
        PageRequest page = PageRequest.of(0, sourceLimit);

        for (EquipmentReservation r : reservationRepository
                .findRecentActivity(page)) {
            boolean cancelled = r.getCancelledAt() != null;
            String what = r.getEquipment().getName() + " " + r.getStartTime()
                    + "–" + r.getEndTime();
            add(out, cancelled ? "RESERVATION_CANCELLED" : "RESERVATION", r.getUser(), what,
                    (cancelled ? r.getCancelledAt() : r.getCreatedAt()).toInstant(), r.getStatus().name());
        }
        for (HardwareCheckIn c : checkInRepository.findRecentActivity(page)) {
            String slot = c.getRequestedStart() == null ? "" : " · " + c.getRequestedStart()
                    + "–" + c.getRequestedEnd();
            add(out, "WAIVER", c.getUser(), c.getEquipment().getName() + " — " + c.getStatus() + slot,
                    c.getUpdatedAt() == null ? c.getCheckinTime() : c.getUpdatedAt(), c.getStatus().name());
        }
        for (PaymentRecord p : paymentRecordRepository.findAll(
                PageRequest.of(0, sourceLimit, org.springframework.data.domain.Sort.by("occurredAt").descending()))) {
            Map<String, Object> row = add(out, "PAYMENT", p.getUser(),
                    (p.getDescription() == null ? p.getKind().name() : p.getDescription())
                            + String.format(" · %.2f %s", p.getAmountCents() / 100.0, p.getCurrency().toUpperCase(java.util.Locale.ROOT)),
                    p.getOccurredAt().toInstant(), p.getStatus().name());
            row.put("receiptUrl", p.getReceiptUrl());
        }
        for (ClassEnrollment e : classEnrollmentRepository.findAll(
                PageRequest.of(0, sourceLimit, org.springframework.data.domain.Sort.by("createdAt").descending()))) {
            add(out, "CLASS_ENROLLMENT", e.getUser(),
                    e.getClassSession().getTitle() + " · " + e.getClassSession().getStartTime(),
                    e.getCreatedAt().toInstant(), e.getStatus());
        }
        for (TrainingCompletion t : trainingCompletionRepository.findAll(
                PageRequest.of(0, sourceLimit, org.springframework.data.domain.Sort.by("completedAt").descending()))) {
            add(out, "TRAINING", t.getUser(), t.getEquipment().getName(),
                    t.getCompletedAt().toInstant(), "COMPLETED");
        }
        for (CardScan s : cardScanRepository.findAllByOrderByScannedAtDesc(page)) {
            Map<String, Object> row = add(out, "CARD_TAP", s.getUser(),
                    s.isApproved() ? "Card tap approved" : "Card tap denied — " + s.getReason(),
                    s.getScannedAt(), s.isApproved() ? "APPROVED" : "DENIED");
            if (s.getUser() == null) row.put("userName", "Unknown card " + s.getCardUid());
        }

        out.sort(Comparator.comparing((Map<String, Object> m) -> (Instant) m.get("time")).reversed());
        return out.subList(0, Math.min(sourceLimit, out.size()));
    }

    private Map<String, Object> add(List<Map<String, Object>> out, String type, User user,
                                    String detail, Instant time, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("userId", user == null ? null : user.getId());
        m.put("userName", user == null ? null : CardService.displayName(user));
        m.put("userEmail", user == null ? null : user.getEmail());
        m.put("detail", detail);
        m.put("status", status);
        m.put("time", time);
        out.add(m);
        return m;
    }
}
