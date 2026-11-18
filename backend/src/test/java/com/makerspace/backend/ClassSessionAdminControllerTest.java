package com.makerspace.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makerspace.backend.config.SecurityConfig;
import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.controller.ClassSessionController;
import com.makerspace.backend.controller.dto.ClassSessionDTO;
import com.makerspace.backend.controller.dto.CreateClassSessionRequest;
import com.makerspace.backend.repository.AppRoleRepository;
import com.makerspace.backend.security.OAuth2FailureHandler;
import com.makerspace.backend.security.OAuth2SuccessHandler;
import com.makerspace.backend.services.ClassSessionService;
import com.makerspace.backend.services.HardwareDeviceService;
import com.makerspace.backend.services.JwtService;
import com.makerspace.backend.services.UserPermissionService;
import com.makerspace.backend.services.UserStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClassSessionController.class)
@Import(SecurityConfig.class)
class ClassSessionAdminControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ClassSessionService classSessionService;
    @MockBean HardwareDeviceService hardwareDeviceService;
    @MockBean JwtService jwtService;
    @MockBean UserStateService userStateService;
    @MockBean UserPermissionService userPermissionService;
    @MockBean AppRoleRepository roleRepository;
    @MockBean OAuth2SuccessHandler oAuth2SuccessHandler;
    @MockBean OAuth2FailureHandler oAuth2FailureHandler;

    @Test
    void adminCanCreateClassSessionThroughDashboardEndpoint() throws Exception {
        ZonedDateTime start = ZonedDateTime.now().plusDays(3);
        CreateClassSessionRequest request = new CreateClassSessionRequest(
                "Ceramics Studio", "Hands-on clay session", 7L,
                start, start.plusHours(2), 12, 2500, 1250,
                "https://example.com/class.jpg", "Studio A");
        when(classSessionService.create(any(CreateClassSessionRequest.class), eq(10L)))
                .thenReturn(dto(99L, request.title(), start));

        mockMvc.perform(post("/api/v1/class-sessions")
                        .with(authentication(auth(10L, "MANAGE_CLASSES")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99L))
                .andExpect(jsonPath("$.title").value("Ceramics Studio"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.maxCapacity").value(12));

        verify(classSessionService).create(any(CreateClassSessionRequest.class), eq(10L));
    }

    @Test
    void memberWithoutManageClassesCannotCreateClassSession() throws Exception {
        CreateClassSessionRequest request = new CreateClassSessionRequest(
                "Ceramics Studio", "Hands-on clay session", 7L,
                ZonedDateTime.now().plusDays(3), ZonedDateTime.now().plusDays(3).plusHours(2),
                12, 2500, 1250, null, "Studio A");

        mockMvc.perform(post("/api/v1/class-sessions")
                        .with(authentication(auth(11L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCreateClassSessionIsRejected() throws Exception {
        CreateClassSessionRequest request = new CreateClassSessionRequest(
                "Ceramics Studio", "Hands-on clay session", 7L,
                ZonedDateTime.now().plusDays(3), ZonedDateTime.now().plusDays(3).plusHours(2),
                12, 2500, 1250, null, "Studio A");

        mockMvc.perform(post("/api/v1/class-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    private static Authentication auth(long userId, String... permissions) {
        List<GrantedAuthority> authorities = Arrays.stream(permissions)
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p))
                .toList();
        var principal = new UserPrincipal(userId, String.valueOf(userId), "user@test.com", authorities);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private static ClassSessionDTO dto(Long id, String title, ZonedDateTime start) {
        return new ClassSessionDTO(id, title, "Hands-on clay session", 7L, "Instructor",
                start, start.plusHours(2), 12, 2500, 1250, "SCHEDULED",
                "https://example.com/class.jpg", "Studio A", ZonedDateTime.now(),
                0L, 12L, 1250, "prod_test", "price_test");
    }
}
