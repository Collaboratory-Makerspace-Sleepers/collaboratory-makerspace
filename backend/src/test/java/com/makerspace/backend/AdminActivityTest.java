package com.makerspace.backend;

import com.makerspace.backend.controller.ActivityFeedController;
import com.makerspace.backend.model.*;
import com.makerspace.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminActivityTest {
    @Test
    void summaryUsesDatabaseCountsAndMatchesTheEntirePendingWaiverQueue() {
        var controller = new ActivityFeedController();
        var reservations = mock(ReservationRepository.class);
        var waivers = mock(HardwareCheckInRepository.class);
        var payments = mock(PaymentRecordRepository.class);
        var classes = mock(ClassEnrollmentRepository.class);
        ReflectionTestUtils.setField(controller, "reservationRepository", reservations);
        ReflectionTestUtils.setField(controller, "checkInRepository", waivers);
        ReflectionTestUtils.setField(controller, "paymentRecordRepository", payments);
        ReflectionTestUtils.setField(controller, "classEnrollmentRepository", classes);
        when(reservations.countByStatusInAndEndTimeAfter(any(), any())).thenReturn(4L);
        when(waivers.countByStatusIn(List.of(HardwareCheckIn.CheckInStatus.NEEDS_WAIVER,
                HardwareCheckIn.CheckInStatus.WAIVER_SIGNED))).thenReturn(3L);
        when(classes.countUpcoming(any())).thenReturn(2L);
        when(payments.countByStatus(PaymentStatus.SUCCEEDED)).thenReturn(10L);
        assertThat(controller.summary()).isEqualTo(new ActivityFeedController.Summary(4, 3, 2, 10));
    }

    @Test
    void feedIncludesCurrentClassWaiverAndPaymentStateWithActualTimestamps() {
        var controller = new ActivityFeedController();
        var reservations = mock(ReservationRepository.class);
        var waivers = mock(HardwareCheckInRepository.class);
        var payments = mock(PaymentRecordRepository.class);
        var cards = mock(CardScanRepository.class);
        var classes = mock(ClassEnrollmentRepository.class);
        var training = mock(TrainingCompletionRepository.class);
        ReflectionTestUtils.setField(controller, "reservationRepository", reservations);
        ReflectionTestUtils.setField(controller, "checkInRepository", waivers);
        ReflectionTestUtils.setField(controller, "paymentRecordRepository", payments);
        ReflectionTestUtils.setField(controller, "cardScanRepository", cards);
        ReflectionTestUtils.setField(controller, "classEnrollmentRepository", classes);
        ReflectionTestUtils.setField(controller, "trainingCompletionRepository", training);
        Instant approval = Instant.parse("2026-11-10T20:00:00Z");
        Equipment machine = new Equipment();
        machine.setName("Harkonnen");
        HardwareCheckIn waiver = new HardwareCheckIn();
        waiver.setEquipment(machine);
        waiver.setStatus(HardwareCheckIn.CheckInStatus.WAIVER_APPROVED);
        waiver.setCheckinTime(approval.minusSeconds(1000));
        waiver.setUpdatedAt(approval);
        PaymentRecord payment = new PaymentRecord();
        payment.setKind(PaymentKind.DAY_PASS);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setAmountCents(2500);
        payment.setOccurredAt(ZonedDateTime.parse("2026-11-10T19:00:00Z"));
        payment.setReceiptUrl("https://pay.stripe.com/receipts/test");
        ClassSession session = new ClassSession();
        session.setTitle("Safety class");
        session.setStartTime(ZonedDateTime.parse("2026-11-28T16:00:00Z"));
        ClassEnrollment enrollment = new ClassEnrollment();
        enrollment.setClassSession(session);
        enrollment.setCreatedAt(ZonedDateTime.parse("2026-11-10T18:00:00Z"));
        enrollment.setStatus("ENROLLED");
        when(reservations.findRecentActivity(any())).thenReturn(Page.empty());
        when(waivers.findRecentActivity(any())).thenReturn(new PageImpl<>(List.of(waiver)));
        when(payments.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(payment)));
        when(classes.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(enrollment)));
        when(training.findAll(any(Pageable.class))).thenReturn(Page.empty());
        when(cards.findAllByOrderByScannedAtDesc(any())).thenReturn(List.of());

        var feed = controller.feed(60);

        assertThat(feed).hasSize(3);
        assertThat(feed.get(0)).containsEntry("status", "WAIVER_APPROVED").containsEntry("time", approval);
        assertThat(feed.get(1)).containsEntry("receiptUrl", payment.getReceiptUrl());
        assertThat(feed.get(2)).containsEntry("type", "CLASS_ENROLLMENT").containsEntry("status", "ENROLLED");
    }
}
