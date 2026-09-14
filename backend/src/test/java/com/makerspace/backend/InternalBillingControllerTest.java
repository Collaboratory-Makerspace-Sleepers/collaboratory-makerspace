package com.makerspace.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makerspace.backend.config.SecurityConfig;
import com.makerspace.backend.config.security.InternalAuthFilter;
import com.makerspace.backend.controller.InternalBillingController;
import com.makerspace.backend.controller.dto.StripeEventCommand;
import com.makerspace.backend.model.EventOutcome;
import com.makerspace.backend.repository.StripeEventLogRepository;
import com.makerspace.backend.security.OAuth2SuccessHandler;
import com.makerspace.backend.services.JwtService;
import com.makerspace.backend.services.MembershipService;
import com.makerspace.backend.services.StripeEventService;
import com.makerspace.backend.services.UserPermissionService;
import com.makerspace.backend.services.UserStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalBillingController.class)
@Import({SecurityConfig.class, InternalAuthFilter.class})
@TestPropertySource(properties = "app.internal.api.secret=test-secret")
class InternalBillingControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean StripeEventService stripeEventService;
    @MockBean MembershipService membershipService;
    @MockBean StripeEventLogRepository eventLogRepository;
    @MockBean JwtService jwtService;
    @MockBean UserStateService userStateService;
    @MockBean UserPermissionService userPermissionService;
    @MockBean OAuth2SuccessHandler oAuth2SuccessHandler;

    private static Authentication internalAuth() {
        return new PreAuthenticatedAuthenticationToken(
                "internal", null,
                List.of(new SimpleGrantedAuthority("SCOPE_INTERNAL")));
    }

    private StripeEventCommand minimalCommand() {
        return new StripeEventCommand(
                "evt_test", "customer.subscription.updated", "2023-10-16",
                true, ZonedDateTime.now(), "EVENTBRIDGE", "{}");
    }

    // -------------------------------------------------------------------------
    // Auth: header-based access control
    // -------------------------------------------------------------------------

    @Test
    void events_noHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/internal/billing/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalCommand())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void events_wrongHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/internal/billing/events")
                        .header(InternalAuthFilter.HEADER, "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalCommand())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void events_correctHeader_returns200() throws Exception {
        when(stripeEventService.handle(any())).thenReturn(EventOutcome.PROCESSED);

        mockMvc.perform(post("/api/internal/billing/events")
                        .header(InternalAuthFilter.HEADER, "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalCommand())))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // POST /events — status contract
    // -------------------------------------------------------------------------

    @Test
    void events_newEvent_returns200() throws Exception {
        when(stripeEventService.handle(any())).thenReturn(EventOutcome.PROCESSED);

        mockMvc.perform(post("/api/internal/billing/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalCommand()))
                        .with(authentication(internalAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void events_duplicateEvent_returns409() throws Exception {
        when(stripeEventService.handle(any())).thenReturn(EventOutcome.DUPLICATE);

        mockMvc.perform(post("/api/internal/billing/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalCommand()))
                        .with(authentication(internalAuth())))
                .andExpect(status().isConflict());
    }

    @Test
    void events_skippedEvent_returns200() throws Exception {
        when(stripeEventService.handle(any())).thenReturn(EventOutcome.SKIPPED);

        mockMvc.perform(post("/api/internal/billing/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalCommand()))
                        .with(authentication(internalAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void events_unprocessablePayload_returns422() throws Exception {
        when(stripeEventService.handle(any()))
                .thenThrow(new ResponseStatusException(UNPROCESSABLE_ENTITY, "No plan found"));

        mockMvc.perform(post("/api/internal/billing/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalCommand()))
                        .with(authentication(internalAuth())))
                .andExpect(status().isUnprocessableEntity());
    }

    // -------------------------------------------------------------------------
    // POST /sweep
    // -------------------------------------------------------------------------

    @Test
    void sweep_withAuth_returns200() throws Exception {
        mockMvc.perform(post("/api/internal/billing/sweep")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asOf\":\"2026-09-13T03:00:00Z\"}")
                        .with(authentication(internalAuth())))
                .andExpect(status().isOk());

        verify(membershipService).applyGracePeriodExpiry(any(ZonedDateTime.class));
    }

    @Test
    void sweep_withEmptyBody_defaultsToNow() throws Exception {
        mockMvc.perform(post("/api/internal/billing/sweep")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(authentication(internalAuth())))
                .andExpect(status().isOk());

        verify(membershipService).applyGracePeriodExpiry(any(ZonedDateTime.class));
    }

    @Test
    void sweep_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/internal/billing/sweep")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // GET /cursor
    // -------------------------------------------------------------------------

    @Test
    void cursor_returnsLastReceivedAt() throws Exception {
        ZonedDateTime ts = ZonedDateTime.parse("2026-09-13T02:00:00Z");
        when(eventLogRepository.findMaxReceivedAt()).thenReturn(Optional.of(ts));

        mockMvc.perform(get("/api/internal/billing/cursor")
                        .with(authentication(internalAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReceivedAt").exists());
    }

    @Test
    void cursor_returnsNullLastReceivedAt_whenLogIsEmpty() throws Exception {
        when(eventLogRepository.findMaxReceivedAt()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/internal/billing/cursor")
                        .with(authentication(internalAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReceivedAt").doesNotExist());
    }

    @Test
    void cursor_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/internal/billing/cursor"))
                .andExpect(status().isUnauthorized());
    }
}
