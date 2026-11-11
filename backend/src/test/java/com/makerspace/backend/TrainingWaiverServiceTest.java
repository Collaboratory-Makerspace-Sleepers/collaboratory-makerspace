package com.makerspace.backend;

import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.TrainingWaiverSignature;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.repository.TrainingWaiverSignatureRepository;
import com.makerspace.backend.repository.UserRepository;
import com.makerspace.backend.services.TrainingWaiverService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.time.LocalDateTime;
import com.makerspace.backend.model.User;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingWaiverServiceTest {

    @Mock private EquipmentRepository equipmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private TrainingWaiverSignatureRepository signatureRepository;

    @InjectMocks private TrainingWaiverService trainingWaiverService;

    @Test
    void sign_returnsExistingSignatureForSameBookingIntent() {
        Long userId = 7L;
        Long equipmentId = 12L;
        ZonedDateTime start = ZonedDateTime.now().plusDays(1);
        ZonedDateTime end = start.plusHours(1);
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setCurrentWaiverVersion(2);
        TrainingWaiverSignature existing = new TrainingWaiverSignature();
        existing.setId(42L);
        existing.setSignerName("Member Name");
        existing.setWaiverVersion(2);
        existing.setSignedAt(LocalDateTime.now());

        when(equipmentRepository.findById(equipmentId)).thenReturn(Optional.of(equipment));
        when(signatureRepository
                .findFirstByUserIdAndEquipmentIdAndStartTimeAndEndTimeAndReservationIdIsNullOrderByIdDesc(
                        userId, equipmentId, start, end))
                .thenReturn(Optional.of(existing));

        TrainingWaiverSignature result =
                trainingWaiverService.sign(userId, equipmentId, "Member Name", start, end, 2);

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).findById(any());
        verify(signatureRepository, never()).save(any());
    }

    @Test
    void signingAgainCreatesFreshSignatureInsteadOfReturningExpiredOne() {
        ZonedDateTime start = ZonedDateTime.now().plusDays(1);
        Equipment equipment = new Equipment();
        equipment.setCurrentWaiverVersion(1);
        TrainingWaiverSignature expired = new TrainingWaiverSignature();
        expired.setSignerName("Member");
        expired.setWaiverVersion(1);
        expired.setSignedAt(LocalDateTime.now().minusDays(1));
        when(equipmentRepository.findById(12L)).thenReturn(Optional.of(equipment));
        when(signatureRepository
                .findFirstByUserIdAndEquipmentIdAndStartTimeAndEndTimeAndReservationIdIsNullOrderByIdDesc(
                        7L, 12L, start, start.plusHours(1)))
                .thenReturn(Optional.of(expired));
        when(userRepository.findById(7L)).thenReturn(Optional.of(new User()));
        when(signatureRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        TrainingWaiverSignature fresh = trainingWaiverService.sign(
                7L, 12L, "Member", start, start.plusHours(1), 1);

        assertThat(fresh).isNotSameAs(expired);
        assertThat(fresh.isValidForBookingAt(LocalDateTime.now())).isTrue();
        assertThat(expired.getSignedAt()).isBefore(LocalDateTime.now().minusHours(23));
    }
}
