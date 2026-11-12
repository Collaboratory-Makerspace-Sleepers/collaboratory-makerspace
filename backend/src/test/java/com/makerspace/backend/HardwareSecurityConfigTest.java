package com.makerspace.backend;

import com.makerspace.backend.config.SecurityConfig;
import com.makerspace.backend.config.security.DeviceKeyFilter;
import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.controller.CardController;
import com.makerspace.backend.security.OAuth2FailureHandler;
import com.makerspace.backend.security.OAuth2SuccessHandler;
import com.makerspace.backend.services.CardService;
import com.makerspace.backend.services.JwtService;
import com.makerspace.backend.services.UserPermissionService;
import com.makerspace.backend.services.UserStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardController.class)
@Import({SecurityConfig.class, DeviceKeyFilter.class})
@TestPropertySource(properties = "app.hardware.device-key=test-device-key")
class HardwareSecurityConfigTest {

    @Autowired MockMvc mockMvc;

    @MockBean CardService cardService;
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
}
