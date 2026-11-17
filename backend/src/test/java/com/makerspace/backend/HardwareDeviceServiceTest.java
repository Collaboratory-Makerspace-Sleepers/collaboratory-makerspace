package com.makerspace.backend;

import com.makerspace.backend.config.security.DevicePrincipal;
import com.makerspace.backend.model.*;
import com.makerspace.backend.repository.*;
import com.makerspace.backend.services.CardService;
import com.makerspace.backend.services.HardwareDeviceService;
import com.makerspace.backend.services.HardwareDeviceService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HardwareDeviceServiceTest {
    private final HardwareDeviceRepository devices = mock(HardwareDeviceRepository.class);
    private final HardwareDeviceEventRepository events = mock(HardwareDeviceEventRepository.class);
    private final EquipmentRepository equipment = mock(EquipmentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MemberCardRepository cards = mock(MemberCardRepository.class);
    private final CardService cardService = mock(CardService.class);
    private final HardwareDeviceService service = new HardwareDeviceService(devices, events, equipment, users, cards, cardService);
    private final HardwareDevice device = new HardwareDevice();
    private final String key = "a".repeat(64);
    private DevicePrincipal principal;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "pairingSecret", "test-pairing-pepper-that-is-not-a-production-secret");
        device.setName("Entrance");
        device.setArea("Main entrance");
        device.setKeyHash(HardwareDeviceService.keyHash(key));
        principal = new DevicePrincipal(device.getId(), device.getKeyHash());
        when(devices.findLocked(device.getId())).thenReturn(Optional.of(device));
        when(devices.findById(device.getId())).thenReturn(Optional.of(device));
        when(devices.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsOneTimeExpiringPairingCodeAndNeverExposesHashesInAdminDto() {
        Pairing created = service.create(new DeviceSettings("Pi", "Workshop", null, true, false, 5));
        assertThat(created.code()).matches("[0-9]{8}");
        assertThat(created.expiresAt()).isAfter(Instant.now().plusSeconds(590));
        assertThat(created.device().doorEnabled()).isFalse();
        assertThat(created.device().toString()).doesNotContain("keyHash", "pairingHash");
    }

    @Test
    void pairReturnsExactPiCredentialShapeAndConsumesCode() {
        var code = service.pairingCode(device.getId()).code();
        when(devices.findForPairing(device.getPairingHash())).thenReturn(Optional.of(device));
        Map<String, Object> paired = service.pair(code, "raspberrypi");
        assertThat(paired.get("id")).isEqualTo(device.getId().toString());
        assertThat((String) paired.get("deviceKey")).matches("[0-9a-f]{64}");
        assertThat(device.getKeyHash()).isNotEqualTo(paired.get("deviceKey"));
        assertThat(device.getPairingHash()).isNull();
        assertThat(device.getPairingExpiresAt()).isNull();
        assertThat(service.authenticate(device.getId().toString(), (String) paired.get("deviceKey"))).isPresent();
    }

    @Test
    void expiredCodesCannotPairAndRotationImmediatelyInvalidatesOldKeys() {
        service.pairingCode(device.getId());
        assertThat(service.authenticate(device.getId().toString(), key)).isEmpty();
        device.setPairingExpiresAt(Instant.now().minusSeconds(1));
        when(devices.findForPairing(anyString())).thenReturn(Optional.of(device));
        assertThatThrownBy(() -> service.pair("12345678", "pi")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.heartbeat(principal, "pi", "Ready")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void wrongKeysAndDeletedDevicesNeverAuthenticateAndDisabledDevicesCannotApprove() {
        assertThat(service.authenticate("bad-id", key)).isEmpty();
        assertThat(service.authenticate(device.getId().toString(), "b".repeat(64))).isEmpty();
        device.setEnabled(false);
        assertThat(service.authenticate(device.getId().toString(), key)).isPresent();
        assertThat(service.heartbeat(principal, "pi", "Ready")).containsEntry("enabled", false).containsEntry("doorEnabled", false);
        assertThat(service.scan(principal, "A1B2")).containsEntry("approved", false);
        verifyNoInteractions(cardService);
        device.setEnabled(true);
        service.delete(device.getId());
        assertThat(service.authenticate(device.getId().toString(), key)).isEmpty();
    }

    @Test
    void assignmentConsumesExactlyOneTapAndNeverGrantsAccessOrDoorOutput() {
        User user = new User();
        user.setId(1L);
        user.setAccountStatus(AccountStatus.ACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        service.startAssignment(device.getId(), 1L, "Member card", 2L);
        device.setDoorEnabled(true);
        Map<String, Object> result = service.scan(principal, "A1B2");
        assertThat(result).containsEntry("approved", false).containsEntry("enrollment", true).containsEntry("status", "CARD_ASSIGNED");
        assertThat(result.get("door")).isEqualTo(Map.of("enabled", false));
        assertThat(device.getAssignmentUserId()).isNull();
        verify(cardService).assign("A1B2", 1L, "Member card", 2L);
        verify(cardService, never()).scanPaired(anyString(), anyString(), any());
        when(cardService.scanPaired("A1B2", device.getId().toString(), null)).thenReturn(new CardService.ScanResult(false, "No membership", null, 1L, 5L));
        assertThat(service.scan(principal, "A1B2")).containsEntry("approved", false).containsEntry("scanId", 5L);
    }

    @Test
    void assignmentRejectsAnotherMembersCardAndExpiresWithoutAccess() {
        User member = new User();
        member.setId(1L);
        member.setAccountStatus(AccountStatus.ACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(member));
        service.startAssignment(device.getId(), 1L, "", 2L);
        User other = new User();
        other.setId(3L);
        MemberCard card = new MemberCard();
        card.setUser(other);
        when(cards.findByCardUid("A1B2")).thenReturn(Optional.of(card));
        assertThat(service.scan(principal, "A1B2")).containsEntry("status", "ASSIGNMENT_REJECTED");
        assertThat(device.getAssignmentUserId()).isEqualTo(1L);
        device.setAssignmentExpiresAt(Instant.now().minusSeconds(1));
        assertThat(service.scan(principal, "A1B2")).containsEntry("status", "ASSIGNMENT_EXPIRED").containsEntry("approved", false);
        verifyNoInteractions(cardService);
    }

    @Test
    void heartbeatDoesNotScanAndLostConnectionIsLoggedThenRestored() {
        service.heartbeat(principal, "pi", "Ready");
        verifyNoInteractions(cardService);
        assertThat(device.isConnected()).isTrue();
        device.setLastSeenAt(Instant.now().minusSeconds(60));
        when(devices.findDisconnected(any())).thenReturn(List.of(device));
        service.monitor();
        assertThat(device.isConnected()).isFalse();
        service.heartbeat(principal, "pi", "Ready");
        assertThat(device.isConnected()).isTrue();
        verify(events).save(argThat(event -> "CONNECTION_LOST".equals(event.getType())));
    }

    @Test
    void doorCommandRequiresApprovedDecisionAndUsesConfiguredEquipmentOnly() {
        device.setEquipmentId(10L);
        device.setDoorEnabled(true);
        when(cardService.scanPaired("A1B2", device.getId().toString(), 10L))
                .thenReturn(new CardService.ScanResult(false, "Training missing", null, 1L, 1L))
                .thenReturn(new CardService.ScanResult(true, "Access granted", "Member", 1L, 2L));
        assertThat(service.scan(principal, "A1B2").get("door")).isEqualTo(Map.of("enabled", false, "unlockSeconds", 5));
        assertThat(service.scan(principal, "A1B2").get("door")).isEqualTo(Map.of("enabled", true, "unlockSeconds", 5));
    }

    @Test
    void eventRetriesAreAcknowledgedWithoutRepeatingWritesOrRequestingAccess() {
        UUID eventId = UUID.randomUUID();
        var item = new EventInput(eventId, Instant.now(), "SERVICE_STARTED", "Ready", "INFO", null, null);
        when(events.existsByDeviceIdAndEventId(device.getId(), eventId)).thenReturn(false, true);
        assertThat(service.ingest(principal, List.of(item))).containsEntry("accepted", List.of(eventId.toString()));
        service.ingest(principal, List.of(item));
        verify(events, times(1)).saveAndFlush(argThat(event -> eventId.equals(event.getEventId()) && "DEVICE".equals(event.getSource())));
        verifyNoInteractions(cardService);
        assertThatThrownBy(() -> service.ingest(principal, List.of())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.scan(principal, "not-hex")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void cancellingAssignmentClearsAllPendingFieldsAndTheNextTapIsANormalScan() {
        User user = new User();
        user.setId(1L);
        user.setAccountStatus(AccountStatus.ACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        service.startAssignment(device.getId(), 1L, "Test", 2L);

        var cancelled = service.cancelAssignment(device.getId());
        assertThat(cancelled.assignmentUserId()).isNull();
        assertThat(cancelled.assignmentExpiresAt()).isNull();
        assertThat(device.getAssignmentStaffId()).isNull();
        assertThat(device.getAssignmentLabel()).isNull();
        when(cardService.scanPaired("A1B2", device.getId().toString(), null))
                .thenReturn(new CardService.ScanResult(false, "Card not registered", null, null, 9L));
        assertThat(service.scan(principal, "A1B2")).containsEntry("status", "DENIED");
        verify(cardService, never()).assign(anyString(), anyLong(), anyString(), anyLong());
    }

    @Test
    void secondAssignmentCannotSilentlyReplaceAWaitingMember() {
        User user = new User();
        user.setId(1L);
        user.setAccountStatus(AccountStatus.ACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        service.startAssignment(device.getId(), 1L, "First", 2L);
        assertThatThrownBy(() -> service.startAssignment(device.getId(), 1L, "Replacement", 2L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("already waiting");
        assertThat(device.getAssignmentLabel()).isEqualTo("First");
    }

    @Test
    void memberDeactivationWhileWaitingRejectsAssignmentWithoutRequestingAccess() {
        User user = new User();
        user.setId(1L);
        user.setAccountStatus(AccountStatus.ACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        service.startAssignment(device.getId(), 1L, "Test", 2L);
        when(users.findById(1L)).thenReturn(Optional.empty());

        assertThat(service.scan(principal, "A1B2"))
                .containsEntry("approved", false).containsEntry("status", "ASSIGNMENT_REJECTED");
        assertThat(device.getAssignmentUserId()).isNull();
        verifyNoInteractions(cardService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 31, -1, Integer.MAX_VALUE})
    void invalidUnlockDurationsAreRejected(int seconds) {
        assertThatThrownBy(() -> service.create(new DeviceSettings("Pi", "Area", null, true, true, seconds)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("1-30");
        verify(devices, never()).saveAndFlush(any());
    }

    @Test
    void oversizedAndInvalidEventBatchesAreRejectedWithoutAccessSideEffects() {
        var event = new EventInput(UUID.randomUUID(), Instant.now(), "CARD_TAP", "Detected", "INFO", null, null);
        assertThatThrownBy(() -> service.ingest(principal, java.util.Collections.nCopies(21, event)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("1-20");
        var invalid = new EventInput(UUID.randomUUID(), Instant.now(), "CARD_TAP", "", "APPROVED", null, null);
        assertThatThrownBy(() -> service.ingest(principal, List.of(invalid)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("outcome");
        verify(events, never()).saveAndFlush(any());
        verifyNoInteractions(cardService);
    }
}
