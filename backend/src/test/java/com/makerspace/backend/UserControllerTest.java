package com.makerspace.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makerspace.backend.config.security.RoleHierarchyConfig;
import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.config.security.UserSecurity;
import com.makerspace.backend.config.security.UserSecurityConfig;
import com.makerspace.backend.controller.UserController;
import com.makerspace.backend.controller.dto.UpdateProfileRequest;
import com.makerspace.backend.controller.dto.UpdateRoleRequest;
import com.makerspace.backend.model.Role;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserProfile;
import com.makerspace.backend.services.JwtService;
import com.makerspace.backend.services.UserService;
import com.makerspace.backend.services.UserStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest for UserController.
 *
 * Authentication is set up via SecurityMockMvcRequestPostProcessors.authentication()
 * with a real UserPrincipal, matching what JwtAuthFilter places on the SecurityContext
 * in production. This ensures isSelf() SpEL checks and currentUserId() behave the same
 * in tests as at runtime.
 *
 * SecurityConfig is imported to bring the RoleHierarchy bean into scope, so that
 * ADMIN → STAFF and ADMIN → INSTRUCTOR hierarchy checks work in tests.
 */
@WebMvcTest(UserController.class)
@Import({UserSecurityConfig.class, UserSecurity.class, RoleHierarchyConfig.class})
class UserControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean JwtService jwtService;            // required by JwtAuthFilter
    @MockBean UserStateService userStateService; // required by JwtAuthFilter

    private User activeUser;
    private User adminUser;

    private static User makeUser(Long id, String email, String firstName, String lastName) {
        UserProfile profile = new UserProfile();
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setProfile(profile);
        return user;
    }

    @BeforeEach
    void setUp() {
        activeUser = makeUser(1L, "member@test.com", "Test", "Member");
        adminUser  = makeUser(2L, "admin@test.com",  "Test", "Admin");
        adminUser.setRoles(Set.of(Role.ADMIN));
    }

    /** Builds an Authentication with a UserPrincipal, matching production JwtAuthFilter output. */
    private static Authentication authFor(long id, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        var principal = new UserPrincipal(id, String.valueOf(id), "user@test.com", authorities);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    // -------------------------------------------------------------------------
    // GET /me
    // -------------------------------------------------------------------------

    @Test
    void getMe_returnsCurrentUserProfile() throws Exception {
        when(userService.findById(1L)).thenReturn(activeUser);

        mockMvc.perform(get("/api/users/me")
                        .with(authentication(authFor(1L, "MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("member@test.com"));
    }

    @Test
    void getMe_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // PATCH /me
    // -------------------------------------------------------------------------

    @Test
    void updateMe_validRequest_returnsUpdatedProfile() throws Exception {
        User updated = makeUser(1L, "member@test.com", "Jane", "Doe");
        when(userService.updateProfile(1L, "Jane", "Doe")).thenReturn(updated);

        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("Jane", "Doe")))
                        .with(authentication(authFor(1L, "MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    void updateMe_blankFirstName_returns400() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("", "Doe")))
                        .with(authentication(authFor(1L, "MEMBER"))))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET /
    // -------------------------------------------------------------------------

    @Test
    void listUsers_asAdmin_returns200WithPage() throws Exception {
        var page = new PageImpl<>(List.of(activeUser), PageRequest.of(0, 50), 1);
        when(userService.findAllActive(any())).thenReturn(page);

        mockMvc.perform(get("/api/users")
                        .with(authentication(authFor(2L, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("member@test.com"));
    }

    @Test
    void listUsers_asMember_returns403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .with(authentication(authFor(1L, "MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_asStaff_returns200() throws Exception {
        when(userService.findAllActive(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/users")
                        .with(authentication(authFor(1L, "STAFF"))))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // Hierarchy acceptance tests — ADMIN implies STAFF (IUM-06/07/08)
    // -------------------------------------------------------------------------

    @Test
    void listUsers_adminPassesStaffEndpoint_viaHierarchy() throws Exception {
        // GET /api/users is guarded by hasRole('STAFF').
        // ADMIN implies STAFF in the hierarchy, so an ADMIN-only principal must pass.
        when(userService.findAllActive(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/users")
                        .with(authentication(authFor(2L, "ADMIN"))))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // GET /{id}
    // -------------------------------------------------------------------------

    @Test
    void getUser_self_returns200() throws Exception {
        when(userService.findById(1L)).thenReturn(activeUser);

        mockMvc.perform(get("/api/users/1")
                        .with(authentication(authFor(1L, "MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getUser_otherMember_returns403() throws Exception {
        mockMvc.perform(get("/api/users/99")
                        .with(authentication(authFor(1L, "MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUser_asAdmin_returnsAnyUser() throws Exception {
        when(userService.findById(1L)).thenReturn(activeUser);

        mockMvc.perform(get("/api/users/1")
                        .with(authentication(authFor(2L, "ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void getUser_notFound_returns404() throws Exception {
        when(userService.findById(99L)).thenThrow(new ResponseStatusException(NOT_FOUND));

        mockMvc.perform(get("/api/users/99")
                        .with(authentication(authFor(2L, "ADMIN"))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // PATCH /{id}/role
    // -------------------------------------------------------------------------

    @Test
    void updateRole_asAdmin_changesRole() throws Exception {
        User promoted = makeUser(1L, "member@test.com", "Test", "Member");
        promoted.setRoles(Set.of(Role.STAFF));
        when(userService.updateRoles(1L, Set.of(Role.STAFF))).thenReturn(promoted);

        mockMvc.perform(patch("/api/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(Set.of(Role.STAFF))))
                        .with(authentication(authFor(2L, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("STAFF"));
    }

    @Test
    void updateRole_selfChange_returns400() throws Exception {
        mockMvc.perform(patch("/api/users/2/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(Set.of(Role.STAFF))))
                        .with(authentication(authFor(2L, "ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRole_asMember_returns403() throws Exception {
        mockMvc.perform(patch("/api/users/2/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(Set.of(Role.STAFF))))
                        .with(authentication(authFor(1L, "MEMBER"))))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // DELETE /{id}
    // -------------------------------------------------------------------------

    @Test
    void deleteUser_self_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/1")
                        .with(authentication(authFor(1L, "MEMBER"))))
                .andExpect(status().isNoContent());

        verify(userService).softDeleteUser(1L, 1L);
    }

    @Test
    void deleteUser_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/1")
                        .with(authentication(authFor(2L, "ADMIN"))))
                .andExpect(status().isNoContent());

        verify(userService).softDeleteUser(1L, 2L);
    }

    @Test
    void deleteUser_otherMember_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/99")
                        .with(authentication(authFor(1L, "MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUser_lastAdmin_returns400() throws Exception {
        doThrow(new ResponseStatusException(BAD_REQUEST, "Cannot delete the last admin"))
                .when(userService).softDeleteUser(2L, 2L);

        mockMvc.perform(delete("/api/users/2")
                        .with(authentication(authFor(2L, "ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // POST /{id}/restore
    // -------------------------------------------------------------------------

    @Test
    void restoreUser_asAdmin_returns200() throws Exception {
        when(userService.restore(1L)).thenReturn(activeUser);

        mockMvc.perform(post("/api/users/1/restore")
                        .with(authentication(authFor(2L, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void restoreUser_notDeleted_returns400() throws Exception {
        when(userService.restore(1L)).thenThrow(new ResponseStatusException(BAD_REQUEST, "User is not deleted"));

        mockMvc.perform(post("/api/users/1/restore")
                        .with(authentication(authFor(2L, "ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restoreUser_asMember_returns403() throws Exception {
        mockMvc.perform(post("/api/users/1/restore")
                        .with(authentication(authFor(1L, "MEMBER"))))
                .andExpect(status().isForbidden());
    }
}