package com.makerspace.backend;

import com.makerspace.backend.config.SecurityConfig;
import com.makerspace.backend.config.security.DeviceKeyFilter;
import com.makerspace.backend.config.security.InternalAuthFilter;
import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.controller.CardController;
import com.makerspace.backend.controller.HardwareDeviceController;
import com.makerspace.backend.config.security.DevicePrincipal;
import com.makerspace.backend.security.OAuth2FailureHandler;
import com.makerspace.backend.security.OAuth2SuccessHandler;
import com.makerspace.backend.security.JwtAuthFilter;
import com.makerspace.backend.services.CardService;
import com.makerspace.backend.services.HardwareDeviceService;
import com.makerspace.backend.services.JwtService;
import com.makerspace.backend.services.UserPermissionService;
import com.makerspace.backend.services.UserStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import org.springframework.http.HttpMethod;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.ArgumentMatchers.*;

@WebMvcTest({CardController.class, HardwareDeviceController.class})
@ContextConfiguration(classes = HardwareSecurityConfigTest.Config.class)
@TestPropertySource(properties = "app.hardware.device-key=test-device-key")
class HardwareSecurityConfigTest {
    @Configuration(proxyBeanMethods = false)
    @Import({SecurityConfig.class, DeviceKeyFilter.class, InternalAuthFilter.class, JwtAuthFilter.class,
            CardController.class, HardwareDeviceController.class})
    static class Config {}

    @Autowired MockMvc mockMvc;

    @MockBean CardService cardService;
    @MockBean HardwareDeviceService hardwareDeviceService;
    @MockBean JwtService jwtService;
    @MockBean UserStateService userStateService;
    @MockBean UserPermissionService userPermissionService;
    @MockBean OAuth2SuccessHandler oAuth2SuccessHandler;
    @MockBean OAuth2FailureHandler oAuth2FailureHandler;

    @Test
    void cardDashboard_acceptsAuthenticatedAdmin() throws Exception {
        when(cardService.listCards()).thenReturn(List.of());
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("MANAGE_EQUIPMENT"));
        var principal = new UserPrincipal(1L, "subject", "admin@example.com", authorities);
        var admin = new UsernamePasswordAuthenticationToken(principal, null, authorities);

        mockMvc.perform(get("/api/v1/hardware/cards").with(authentication(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void cardDashboard_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/hardware/cards"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deviceCardScan_stillRequiresDeviceKey() throws Exception {
        mockMvc.perform(post("/api/v1/hardware/card-scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardUid\":\"A1B2\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protocolIsPublicButHeartbeatAndAdminDevicesAreNot() throws Exception {
        mockMvc.perform(get("/api/v1/hardware/device/protocol"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocol").value("makerspace-device-v1"))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(get("/api/v1/hardware/devices")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/hardware/device/heartbeat")
                .contentType(MediaType.APPLICATION_JSON).content("{\"hostname\":\"pi\",\"readerStatus\":\"Ready\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pairedCardScanUsesRegisteredDeviceAndIgnoresSpoofedBodyEquipment() throws Exception {
        String id = UUID.randomUUID().toString();
        String key = "a".repeat(64);
        var principal = new DevicePrincipal(UUID.fromString(id), "hashed-key");
        when(hardwareDeviceService.authenticate(id, key)).thenReturn(Optional.of(principal));
        when(hardwareDeviceService.scan(principal, "A1B2")).thenReturn(
                Map.of("approved", false, "message", "Card not registered", "door", Map.of("enabled", false)));
        mockMvc.perform(post("/api/v1/hardware/card-scan").header("X-Device-Id", id).header("X-Device-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardUid\":\"A1B2\",\"equipmentId\":999,\"deviceId\":\"foreign\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approved").value(false));
    }

    @Test
    void invalidPairedCredentialsCannotFallBackToLegacyKey() throws Exception {
        when(hardwareDeviceService.authenticate(anyString(), anyString())).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/v1/hardware/card-scan").header("X-Device-Id", UUID.randomUUID().toString())
                        .header("X-Device-Key", "test-device-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cardUid\":\"A1B2\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/hardware/device/heartbeat").header("X-Device-Key", "test-device-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"hostname\":\"pi\",\"readerStatus\":\"Ready\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deviceCredentialsCannotManageDevicesOrUseLegacyUserCheckin() throws Exception {
        String id = UUID.randomUUID().toString();
        String key = "b".repeat(64);
        when(hardwareDeviceService.authenticate(id, key)).thenReturn(Optional.of(
                new DevicePrincipal(UUID.fromString(id), "hashed-key")));
        mockMvc.perform(get("/api/v1/hardware/devices").header("X-Device-Id", id).header("X-Device-Key", key))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/hardware/checkin").header("X-Device-Id", id).header("X-Device-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":1,\"equipmentId\":1}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/devices",
            "POST,/devices",
            "PATCH,/devices/11111111-1111-4111-8111-111111111111",
            "DELETE,/devices/11111111-1111-4111-8111-111111111111",
            "POST,/devices/11111111-1111-4111-8111-111111111111/pairing-code",
            "POST,/devices/11111111-1111-4111-8111-111111111111/assignment",
            "DELETE,/devices/11111111-1111-4111-8111-111111111111/assignment",
            "GET,/devices/events"
    })
    void signedInMemberCannotManagePis(String method, String path) throws Exception {
        var principal = new UserPrincipal(3L, "member", "member@example.test", List.of());
        var member = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        mockMvc.perform(request(HttpMethod.valueOf(method), "/api/v1/hardware" + path)
                        .with(authentication(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pi\",\"area\":\"Entrance\",\"userId\":3}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void pairedHeartbeatAndEventUploadUseAuthenticatedPiIdentity() throws Exception {
        String id = UUID.randomUUID().toString();
        String key = "c".repeat(64);
        var principal = new DevicePrincipal(UUID.fromString(id), "hash");
        when(hardwareDeviceService.authenticate(id, key)).thenReturn(Optional.of(principal));
        when(hardwareDeviceService.heartbeat(principal, "pi", "Ready"))
                .thenReturn(Map.of("enabled", true, "doorEnabled", false));
        mockMvc.perform(post("/api/v1/hardware/device/heartbeat")
                        .header("X-Device-Id", id).header("X-Device-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"hostname\":\"pi\",\"readerStatus\":\"Ready\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.doorEnabled").value(false));
        UUID eventId = UUID.randomUUID();
        when(hardwareDeviceService.ingest(eq(principal), anyList())).thenReturn(Map.of("accepted", List.of(eventId.toString())));
        mockMvc.perform(post("/api/v1/hardware/device/events")
                        .header("X-Device-Id", id).header("X-Device-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"eventId\":\"" + eventId
                                + "\",\"occurredAt\":\"2026-11-15T20:00:00Z\",\"type\":\"SERVICE_STARTED\","
                                + "\"detail\":\"Ready\",\"outcome\":\"INFO\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted[0]").value(eventId.toString()));
        verify(hardwareDeviceService).ingest(eq(principal), argThat(batch ->
                batch.size() == 1 && batch.get(0).eventId().equals(eventId)));
    }

    @Test
    void pairingRateLimitStopsEleventhAttemptWithoutTrustingForwardedIpHeaders() throws Exception {
        when(hardwareDeviceService.pair("00000000", "pi")).thenThrow(
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid or expired Pi pairing code"));
        for (int attempt = 0; attempt < 11; attempt++) {
            int current = attempt;
            mockMvc.perform(post("/api/v1/hardware/device/pair")
                            .with(req -> { req.setRemoteAddr("192.0.2.77"); return req; })
                            .header("X-Forwarded-For", "192.0.2." + current)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"00000000\",\"hostname\":\"pi\"}"))
                    .andExpect(status().is(attempt < 10 ? 401 : 429));
        }
        verify(hardwareDeviceService, times(10)).pair("00000000", "pi");
    }

    @Test
    void adminAssignmentRecordsTheSignedInStaffIdentity() throws Exception {
        UUID id = UUID.randomUUID();
        List<GrantedAuthority> permissions = List.of(new SimpleGrantedAuthority("MANAGE_EQUIPMENT"));
        var principal = new UserPrincipal(7L, "admin", "admin@example.test", permissions);
        var admin = new UsernamePasswordAuthenticationToken(principal, null, permissions);
        mockMvc.perform(post("/api/v1/hardware/devices/" + id + "/assignment")
                        .with(authentication(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":3,\"label\":\"Member card\",\"staffId\":999}"))
                .andExpect(status().isOk());
        verify(hardwareDeviceService).startAssignment(id, 3L, "Member card", 7L);
        verify(hardwareDeviceService, never()).startAssignment(any(), any(), any(), eq(999L));
    }
}
