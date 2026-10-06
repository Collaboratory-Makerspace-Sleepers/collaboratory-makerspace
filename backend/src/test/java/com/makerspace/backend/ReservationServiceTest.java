package com.makerspace.backend;

import com.makerspace.backend.model.*;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.repository.ReservationRepository;
import com.makerspace.backend.repository.TrainingTaskRepository;
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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private TrainingTaskRepository trainingTaskRepository;
    @Mock private TrainingWaiverSignatureRepository trainingWaiverSignatureRepository;
    @Mock private MembershipService membershipService;

    @InjectMocks
    private ReservationService reservationService;

    private static final ZonedDateTime START = ZonedDateTime.now().plusHours(1);
    private static final ZonedDateTime END   = ZonedDateTime.now().plusHours(2);

    private Equipment activeEquipment() {
        Equipment e = new Equipment();
        e.setId(1L);
        e.setStatus(EquipmentStatus.AVAILABLE);
        return e;
    }

    private User user() {
        User u = new User();
        u.setId(1L);
        return u;
    }

    @Test
    void create_throws402_whenUserHasNoMembership() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(activeEquipment()));
        when(trainingWaiverSignatureRepository.existsByUserIdAndEquipmentId(1L, 1L)).thenReturn(true);
        when(membershipService.hasActiveMembership(1L)).thenReturn(false);

        assertThatThrownBy(() -> reservationService.create(1L, 1L, START, END))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("membership");
    }

    @Test
    void create_throws402_whenMembershipPeriodExpired() {
        // hasActiveMembership checks both status and date — returning false covers expired periods
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(activeEquipment()));
        when(trainingWaiverSignatureRepository.existsByUserIdAndEquipmentId(1L, 1L)).thenReturn(true);
        when(membershipService.hasActiveMembership(1L)).thenReturn(false);

        assertThatThrownBy(() -> reservationService.create(1L, 1L, START, END))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("membership");
    }

    @Test
    void create_succeeds_withActiveMembership() {
        when(trainingWaiverSignatureRepository.existsByUserIdAndEquipmentId(1L, 1L)).thenReturn(true);
        when(membershipService.hasActiveMembership(1L)).thenReturn(true);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(activeEquipment()));
        when(reservationRepository.findOverlapping(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentReservation result = reservationService.create(1L, 1L, START, END);

        assertThat(result).isNotNull();
    }

    @Test
    void create_addsTrainingTaskForLaserEquipmentAfterMembershipCheck() {
        Equipment laser = activeEquipment();
        laser.setTrainingRequired(true);
        when(trainingWaiverSignatureRepository.existsByUserIdAndEquipmentId(1L, 1L)).thenReturn(true);
        when(membershipService.hasActiveMembership(1L)).thenReturn(true);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(laser));
        when(reservationRepository.findOverlapping(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reservationService.create(1L, 1L, START, END);

        verify(trainingTaskRepository).save(any(TrainingTask.class));
    }

    @Test
    void create_requiresWaiverBeforeMembershipCheck_forNonTrainingEquipment() {
        Equipment printer = activeEquipment();
        printer.setCategory("3D_PRINTER");
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(printer));
        when(trainingWaiverSignatureRepository.existsByUserIdAndEquipmentId(1L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> reservationService.create(1L, 1L, START, END))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("waiver");
        verify(membershipService, org.mockito.Mockito.never()).hasActiveMembership(1L);
    }
}
