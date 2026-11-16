package com.makerspace.backend;

import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.services.EquipmentService;
import com.makerspace.backend.services.MembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EquipmentCatalogTest {
    private final EquipmentService service = new EquipmentService();
    private final EquipmentRepository repository = mock(EquipmentRepository.class);
    private final MembershipService membership = mock(MembershipService.class);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "equipmentRepository", repository);
        ReflectionTestUtils.setField(service, "membershipService", membership);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private Equipment equipment() {
        Equipment item = new Equipment();
        item.setName("New laser");
        item.setCategory("LASER");
        item.setHourlyRateCents(725);
        item.setMemberRateCents(100);
        return item;
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not a URL", "file:///video.mp4", "https:///missing-host"})
    void trainingRequiresAnHttpVideoOnCreateAndUpdate(String video) {
        Equipment item = equipment();
        item.setRequiresTraining(true);
        item.setTrainingVideoUrl(video);
        assertThatThrownBy(() -> service.create(item)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("training video");
        assertThatThrownBy(() -> service.update(1L, item)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("training video");
        verify(repository, never()).save(any());
    }

    @Test
    void createsAndUpdatesOnePriceAndPersistsTrainingVideo() {
        Equipment item = equipment();
        item.setRequiresTraining(true);
        item.setTrainingVideoUrl("https://example.test/laser-training.mp4");
        Equipment created = service.create(item);
        assertThat(created.getMemberRateCents()).isEqualTo(725);
        when(repository.findById(1L)).thenReturn(Optional.of(created));
        Equipment update = equipment();
        update.setHourlyRateCents(1225);
        update.setFreeWithMembership(true);
        update.setRequiresTraining(true);
        update.setTrainingVideoUrl("https://example.test/new-training.mp4");
        Equipment saved = service.update(1L, update);
        assertThat(saved.getHourlyRateCents()).isEqualTo(1225);
        assertThat(saved.getMemberRateCents()).isEqualTo(1225);
        assertThat(saved.getTrainingVideoUrl()).isEqualTo(update.getTrainingVideoUrl());
        assertThat(saved.isFreeWithMembership()).isTrue();
    }

    @Test
    void samePayingRateAndExistingFreeAccessExemptionsAreAppliedToApiPrices() {
        Equipment item = equipment();
        assertThat(EquipmentService.resolveRate(item, false, false)).isEqualTo(725);
        assertThat(EquipmentService.resolveRate(item, true, false)).isEqualTo(725);
        assertThat(EquipmentService.resolveRate(item, true, true)).isZero();
        item.setFreeWithMembership(true);
        assertThat(EquipmentService.resolveRate(item, true, false)).isZero();
        assertThat(EquipmentService.resolveRate(item, false, false)).isEqualTo(725);
        when(membership.hasActiveMembership(1L)).thenReturn(true);
        assertThat(service.forViewer(List.of(item), 1L).get(0).getResolvedHourlyRateCents()).isZero();
        assertThat(item.getHourlyRateCents()).isEqualTo(725);
    }

    @Test
    void catalogListsNewestCreatedFirst() {
        service.findAll();
        verify(repository).findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    @Test
    void nonTrainingEquipmentNeedsNoVideoAndNegativePriceCannotBeSaved() {
        service.create(equipment());
        Equipment invalid = equipment();
        invalid.setHourlyRateCents(-1);
        assertThatThrownBy(() -> service.create(invalid)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("negative");
    }
}
