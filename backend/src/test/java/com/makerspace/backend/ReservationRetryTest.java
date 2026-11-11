package com.makerspace.backend;

import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.EquipmentReservation;
import com.makerspace.backend.model.ReservationStatus;
import com.makerspace.backend.model.TrainingWaiverSignature;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.repository.ReservationRepository;
import com.makerspace.backend.repository.TrainingWaiverSignatureRepository;
import com.makerspace.backend.services.MembershipService;
import com.makerspace.backend.services.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationRetryTest {
    @Test
    void retryReturnsExistingBookingButCannotReuseWaiverForAnotherSlot() {
        ReservationService service = new ReservationService();
        MembershipService membership = mock(MembershipService.class);
        EquipmentRepository equipment = mock(EquipmentRepository.class);
        TrainingWaiverSignatureRepository signatures = mock(TrainingWaiverSignatureRepository.class);
        ReservationRepository reservations = mock(ReservationRepository.class);
        ReflectionTestUtils.setField(service, "membershipService", membership);
        ReflectionTestUtils.setField(service, "equipmentRepository", equipment);
        ReflectionTestUtils.setField(service, "waiverSignatureRepository", signatures);
        ReflectionTestUtils.setField(service, "reservationRepository", reservations);
        when(membership.hasActiveMembership(1L)).thenReturn(true);
        when(equipment.findById(2L)).thenReturn(Optional.of(new Equipment()));
        ZonedDateTime start = ZonedDateTime.now().plusDays(1);
        EquipmentReservation existing = new EquipmentReservation();
        existing.setStatus(ReservationStatus.TRAINING_PENDING);
        existing.setStartTime(start);
        existing.setEndTime(start.plusHours(1));
        TrainingWaiverSignature signature = new TrainingWaiverSignature();
        signature.setReservation(existing);
        when(signatures.findForBooking(3L, 1L, 2L)).thenReturn(Optional.of(signature));

        assertSame(existing, service.create(1L, 2L, start, start.plusHours(1), 3L));
        assertThrows(ResponseStatusException.class,
                () -> service.create(1L, 2L, start.plusHours(2), start.plusHours(3), 3L));
        verify(reservations, never()).save(any());
    }
}
