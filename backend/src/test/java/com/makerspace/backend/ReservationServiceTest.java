package com.makerspace.backend;

import com.makerspace.backend.model.*;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.repository.ReservationRepository;
import com.makerspace.backend.repository.TrainingCompletionRepository;
import com.makerspace.backend.repository.TrainingWaiverSignatureRepository;
import com.makerspace.backend.repository.UserRepository;
import com.makerspace.backend.services.MembershipService;
import com.makerspace.backend.services.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private MembershipService membershipService;
    @Mock private TrainingCompletionRepository trainingCompletionRepository;
    @Mock private TrainingWaiverSignatureRepository waiverSignatureRepository;

    @InjectMocks
    private ReservationService reservationService;

    private static final ZonedDateTime START = ZonedDateTime.now().plusHours(1);
    private static final ZonedDateTime END   = ZonedDateTime.now().plusHours(2);
    private static final Long WAIVER_ID = 10L;

    private Equipment activeEquipment() {
        Equipment e = new Equipment();
        e.setId(1L);
        e.setStatus(EquipmentStatus.AVAILABLE);
        e.setCurrentWaiverVersion(1);
        return e;
    }

    private Equipment trainingEquipment() {
        Equipment e = activeEquipment();
        e.setRequiresTraining(true);
        return e;
    }

    private User user() {
        User u = new User();
        u.setId(1L);
        return u;
    }

    private TrainingWaiverSignature freshWaiver(Equipment equipment) {
        TrainingWaiverSignature w = new TrainingWaiverSignature();
        w.setId(WAIVER_ID);
        w.setWaiverVersion(1);
        w.setSignedAt(LocalDateTime.now());
        w.setStartTime(START);
        w.setEndTime(END);
        w.setEquipment(equipment);
        return w;
    }

    @Test
    void create_throws402_whenUserHasNoMembership() {
        when(membershipService.hasActiveMembership(1L)).thenReturn(false);

        assertThatThrownBy(() -> reservationService.create(1L, 1L, START, END, WAIVER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("membership");
    }

    @Test
    void create_throws402_whenMembershipPeriodExpired() {
        when(membershipService.hasActiveMembership(1L)).thenReturn(false);

        assertThatThrownBy(() -> reservationService.create(1L, 1L, START, END, WAIVER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("membership");
    }

    @Test
    void create_throws400_whenWaiverNotFound() {
        when(membershipService.hasActiveMembership(1L)).thenReturn(true);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(activeEquipment()));
        when(waiverSignatureRepository.findByIdAndUserIdAndEquipmentIdAndReservationIdIsNull(
                WAIVER_ID, 1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.create(1L, 1L, START, END, WAIVER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("waiver");
    }

    @Test
    void create_throws409_whenWaiverVersionStale() {
        Equipment equipment = activeEquipment();
        equipment.setCurrentWaiverVersion(2);

        TrainingWaiverSignature staleWaiver = freshWaiver(equipment);
        staleWaiver.setWaiverVersion(1); // signed with version 1, equipment now at version 2

        when(membershipService.hasActiveMembership(1L)).thenReturn(true);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));
        when(waiverSignatureRepository.findByIdAndUserIdAndEquipmentIdAndReservationIdIsNull(
                WAIVER_ID, 1L, 1L)).thenReturn(Optional.of(staleWaiver));

        assertThatThrownBy(() -> reservationService.create(1L, 1L, START, END, WAIVER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("outdated");
    }

    @Test
    void create_throws400_whenWaiverExpired() {
        Equipment equipment = activeEquipment();
        TrainingWaiverSignature expiredWaiver = freshWaiver(equipment);
        expiredWaiver.setSignedAt(LocalDateTime.now().minusMinutes(31)); // signed 31 min ago

        when(membershipService.hasActiveMembership(1L)).thenReturn(true);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));
        when(waiverSignatureRepository.findByIdAndUserIdAndEquipmentIdAndReservationIdIsNull(
                WAIVER_ID, 1L, 1L)).thenReturn(Optional.of(expiredWaiver));

        assertThatThrownBy(() -> reservationService.create(1L, 1L, START, END, WAIVER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void create_succeeds_withActiveMembership() {
        Equipment equipment = activeEquipment();
        when(membershipService.hasActiveMembership(1L)).thenReturn(true);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));
        when(waiverSignatureRepository.findByIdAndUserIdAndEquipmentIdAndReservationIdIsNull(
                WAIVER_ID, 1L, 1L)).thenReturn(Optional.of(freshWaiver(equipment)));
        when(reservationRepository.findOverlappingByStatuses(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(waiverSignatureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentReservation result = reservationService.create(1L, 1L, START, END, WAIVER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void create_returnsTrainingPending_forFirstBookingOnTrainingRequiredEquipment() {
        Equipment equipment = trainingEquipment();
        when(membershipService.hasActiveMembership(1L)).thenReturn(true);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));
        when(waiverSignatureRepository.findByIdAndUserIdAndEquipmentIdAndReservationIdIsNull(
                WAIVER_ID, 1L, 1L)).thenReturn(Optional.of(freshWaiver(equipment)));
        when(trainingCompletionRepository.existsByUserIdAndEquipmentId(1L, 1L)).thenReturn(false);
        when(reservationRepository.findOverlappingByStatuses(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(waiverSignatureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentReservation result = reservationService.create(1L, 1L, START, END, WAIVER_ID);

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.TRAINING_PENDING);
    }

    @Test
    void create_returnsActive_forRepeatBookingOnTrainingRequiredEquipment() {
        Equipment equipment = trainingEquipment();
        when(membershipService.hasActiveMembership(1L)).thenReturn(true);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));
        when(waiverSignatureRepository.findByIdAndUserIdAndEquipmentIdAndReservationIdIsNull(
                WAIVER_ID, 1L, 1L)).thenReturn(Optional.of(freshWaiver(equipment)));
        when(trainingCompletionRepository.existsByUserIdAndEquipmentId(1L, 1L)).thenReturn(true);
        when(reservationRepository.findOverlappingByStatuses(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(waiverSignatureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentReservation result = reservationService.create(1L, 1L, START, END, WAIVER_ID);

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }
}
