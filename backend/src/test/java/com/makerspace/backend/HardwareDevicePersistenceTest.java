package com.makerspace.backend;

import com.makerspace.backend.model.*;
import com.makerspace.backend.repository.*;
import com.makerspace.backend.services.CardService;
import com.makerspace.backend.services.HardwareDeviceService;
import com.makerspace.backend.services.HardwareCheckInService;
import com.makerspace.backend.services.HardwareDeviceService.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-only-pairing-secret-never-use-in-production"})
@ContextConfiguration(classes = HardwareDevicePersistenceTest.Config.class)
class HardwareDevicePersistenceTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = HardwareDevice.class)
    @EnableJpaRepositories(basePackageClasses = HardwareDeviceRepository.class)
    @Import({HardwareDeviceService.class, CardService.class})
    static class Config {}

    @Autowired HardwareDeviceService service;
    @Autowired HardwareDeviceRepository devices;
    @Autowired HardwareDeviceEventRepository events;
    @Autowired UserRepository users;
    @Autowired MemberCardRepository cards;
    @Autowired CardScanRepository scans;
    @Autowired TestEntityManager entityManager;
    @SpyBean CardService cardService;
    @MockBean HardwareCheckInService checkInService;

    @Test
    void credentialsAndSingleUsePairingPersistAndRevocationIsImmediate() {
        Pairing created = service.create(new DeviceSettings("Pi", "Entrance", null, true, false, 5));
        entityManager.flush();
        entityManager.clear();
        var result = service.pair(created.code(), "raspberrypi");
        String key = (String) result.get("deviceKey");
        entityManager.flush();
        entityManager.clear();
        assertThat(service.authenticate(created.device().id().toString(), key)).isPresent();
        assertThatThrownBy(() -> service.pair(created.code(), "another-pi")).isInstanceOf(ResponseStatusException.class);
        assertThat(devices.findById(created.device().id()).orElseThrow().getKeyHash()).isNotEqualTo(key);
        service.pairingCode(created.device().id());
        entityManager.flush();
        entityManager.clear();
        assertThat(service.authenticate(created.device().id().toString(), key)).isEmpty();
    }

    @Test
    void queuedEventsArePersistedOnceAndMonitorRecordsOnlyOneLostConnection() {
        Pairing created = service.create(new DeviceSettings("Pi", "Entrance", null, true, false, 5));
        var paired = service.pair(created.code(), "pi");
        var principal = service.authenticate(created.device().id().toString(), (String) paired.get("deviceKey")).orElseThrow();
        UUID eventId = UUID.randomUUID();
        var event = new EventInput(eventId, Instant.now(), "CARD_TAP", "Detected", "INFO", "A1B2", null);
        service.ingest(principal, List.of(event));
        entityManager.flush();
        entityManager.clear();
        service.ingest(principal, List.of(event));
        assertThat(events.findAll().stream().filter(e -> e.getEventId().equals(eventId))).hasSize(1);
        devices.findById(created.device().id()).orElseThrow().setLastSeenAt(Instant.now().minusSeconds(60));
        entityManager.flush();
        service.monitor();
        service.monitor();
        entityManager.flush();
        assertThat(events.findAll().stream().filter(e -> e.getType().equals("CONNECTION_LOST"))).hasSize(1);
        service.heartbeat(principal, "pi", "Ready");
        assertThat(devices.findById(created.device().id()).orElseThrow().isConnected()).isTrue();
        verifyNoInteractions(cardService);
    }

    @Test
    void nextTapAssignmentIsBoundToTheChosenPiAndDoesNotGrantAccess() {
        User user = new User();
        user.setEmail("pi-assignment-test@example.test");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user = users.saveAndFlush(user);
        Pairing first = service.create(new DeviceSettings("First", "Entrance", null, true, false, 5));
        Pairing second = service.create(new DeviceSettings("Second", "Workshop", null, true, false, 5));
        var one = service.pair(first.code(), "first");
        var two = service.pair(second.code(), "second");
        var p1 = service.authenticate(first.device().id().toString(), (String) one.get("deviceKey")).orElseThrow();
        var p2 = service.authenticate(second.device().id().toString(), (String) two.get("deviceKey")).orElseThrow();
        service.startAssignment(first.device().id(), user.getId(), "Test", user.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(service.scan(p2, "A1B2"))
                .containsEntry("approved", false).containsEntry("message", "Card not registered");
        verify(cardService, never()).assign(anyString(), anyLong(), anyString(), anyLong());
        assertThat(service.scan(p1, "A1B2")).containsEntry("status", "CARD_ASSIGNED").containsEntry("approved", false);
        verify(cardService).assign("A1B2", user.getId(), "Test", user.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(devices.findById(first.device().id()).orElseThrow().getAssignmentUserId()).isNull();
        MemberCard assigned = cards.findByCardUid("A1B2").orElseThrow();
        assertThat(assigned.getUser().getId()).isEqualTo(user.getId());
        assertThat(assigned.getAssignedBy().getId()).isEqualTo(user.getId());
        assertThat(assigned.getLabel()).isEqualTo("Test");
        assertThat(assigned.isActive()).isTrue();
        assertThat(scans.findAll()).hasSize(1);
        assertThat(scans.findAll().get(0).isApproved()).isFalse();
    }

    @Test
    void deletingPiRevokesCredentialsButPreservesItsEventHistory() {
        Pairing created = service.create(new DeviceSettings("Retired Pi", "Entrance", null, true, false, 5));
        var paired = service.pair(created.code(), "pi");
        var principal = service.authenticate(created.device().id().toString(), (String) paired.get("deviceKey")).orElseThrow();
        service.heartbeat(principal, "pi", "Ready");
        service.delete(created.device().id());
        entityManager.flush();
        entityManager.clear();

        assertThat(service.list()).isEmpty();
        assertThat(service.authenticate(created.device().id().toString(), (String) paired.get("deviceKey"))).isEmpty();
        assertThat(devices.findById(created.device().id()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(service.recent(100)).anySatisfy(event -> {
            assertThat(event.deviceId()).isEqualTo(created.device().id());
            assertThat(event.type()).isEqualTo("DEVICE_DELETED");
            assertThat(event.source()).isEqualTo("SERVER");
        });
    }
}
