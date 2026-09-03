package com.makerspace.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makerspace.backend.config.SecurityConfig;
import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.config.security.UserSecurity;
import com.makerspace.backend.controller.UserController;
import com.makerspace.backend.controller.dto.UpdateProfileRequest;
import com.makerspace.backend.controller.dto.UpdateRoleRequest;
import com.makerspace.backend.model.AppRole;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserProfile;
import com.makerspace.backend.repository.AppRoleRepository;
import com.makerspace.backend.security.OAuth2SuccessHandler;
import com.makerspace.backend.services.JwtService;
import com.makerspace.backend.services.UserPermissionService;
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
import java.util.HashSet;
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
 * Authentication is built with permission-code authorities (matching what
 * JwtAuthFilter now places on the SecurityContext after loading from DB/cache).
 * There is no role hierarchy — access is granted purely by possession of the
 * relevant permission code.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, UserSecurity.class})
class UserControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean JwtService jwtService;
    @MockBean UserStateService userStateService;
    @MockBean UserPermissionService userPermissionService;
    @MockBean AppRoleRepository roleRepository;
    @MockBean OAuth2SuccessHandler oAuth2SuccessHandler; // required by SecurityConfig

    private User activeUser;

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
    }

    /**
     * Builds an Authentication whose authorities are permission codes, exactly
     * as JwtAuthFilter produces in production.
     */
    private static Authentication authWithPermissions(long userId, String... permissions) {
        List<GrantedAuthority> authorities = Arrays.stream(permissions)
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p))
                .toList();
        var principal = new UserPrincipal(userId, String.valueOf(userId), "user@test.com", authorities);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    /** Authenticated with no permissions (a plain member). */
    private static Authentication memberAuth(long userId) {
        return authWithPermissions(userId);
    }

    // -------------------------------------------------------------------------
    // GET /me
    // -------------------------------------------------------------------------

    @Test
    void getMe_returnsCurrentUserProfile() throws Exception {
        when(userService.findById(1L)).thenReturn(activeUser);

        mockMvc.perform(get("/api/v1/users/me")
                        .with(authentication(memberAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("member@test.com"));
    }

    @Test
    void getMe_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // PATCH /me
    // -------------------------------------------------------------------------

    @Test
    void updateMe_validRequest_returnsUpdatedProfile() throws Exception {
        User updated = makeUser(1L, "member@test.com", "Jane", "Doe");
        when(userService.updateProfile(1L, "Jane", "Doe")).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("Jane", "Doe")))
                        .with(authentication(memberAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    void updateMe_blankFirstName_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("", "Doe")))
                        .with(authentication(memberAuth(1L))))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET /  (requires MANAGE_USERS)
    // -------------------------------------------------------------------------

    @Test
    void listUsers_withManageUsers_returns200() throws Exception {
        var page = new PageImpl<>(List.of(activeUser), PageRequest.of(0, 50), 1);
        when(userService.findAllActive(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/users")
                        .with(authentication(authWithPermissions(2L, "MANAGE_USERS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("member@test.com"));
    }

    @Test
    void listUsers_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .with(authentication(memberAuth(1L))))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // GET /{id}  (requires MANAGE_USERS or self)
    // -------------------------------------------------------------------------

    @Test
    void getUser_self_returns200() throws Exception {
        when(userService.findById(1L)).thenReturn(activeUser);

        mockMvc.perform(get("/api/v1/users/1")
                        .with(authentication(memberAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getUser_otherMember_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users/99")
                        .with(authentication(memberAuth(1L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUser_withManageUsers_returnsAnyUser() throws Exception {
        when(userService.findById(1L)).thenReturn(activeUser);

        mockMvc.perform(get("/api/v1/users/1")
                        .with(authentication(authWithPermissions(2L, "MANAGE_USERS"))))
                .andExpect(status().isOk());
    }

    @Test
    void getUser_notFound_returns404() throws Exception {
        when(userService.findById(99L)).thenThrow(new ResponseStatusException(NOT_FOUND));

        mockMvc.perform(get("/api/v1/users/99")
                        .with(authentication(authWithPermissions(2L, "MANAGE_USERS"))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // PATCH /{id}/role  (requires MANAGE_ROLES)
    // -------------------------------------------------------------------------

    @Test
    void updateRole_withManageRoles_changesRole() throws Exception {
        AppRole staffRole = new AppRole();
        staffRole.setCode("STAFF");
        staffRole.setDescription("Staff");

        User promoted = makeUser(1L, "member@test.com", "Test", "Member");
        promoted.setRoles(new HashSet<>(Set.of(staffRole)));

        when(roleRepository.findAllById(Set.of("STAFF"))).thenReturn(List.of(staffRole));
        when(userService.updateRoles(eq(1L), any())).thenReturn(promoted);
        when(userService.findById(1L)).thenReturn(promoted);

        mockMvc.perform(patch("/api/v1/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(Set.of("STAFF"))))
                        .with(authentication(authWithPermissions(2L, "MANAGE_ROLES"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("STAFF"));
    }

    @Test
    void updateRole_selfChange_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/2/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(Set.of("STAFF"))))
                        .with(authentication(authWithPermissions(2L, "MANAGE_ROLES"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRole_withoutPermission_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/users/2/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(Set.of("STAFF"))))
                        .with(authentication(memberAuth(1L))))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // DELETE /{id}
    // -------------------------------------------------------------------------

    @Test
    void deleteUser_self_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/1")
                        .with(authentication(memberAuth(1L))))
                .andExpect(status().isNoContent());

        verify(userService).softDeleteUser(1L, 1L);
    }

    @Test
    void deleteUser_withManageUsers_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/1")
                        .with(authentication(authWithPermissions(2L, "MANAGE_USERS"))))
                .andExpect(status().isNoContent());

        verify(userService).softDeleteUser(1L, 2L);
    }

    @Test
    void deleteUser_otherMember_withoutPermission_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/users/99")
                        .with(authentication(memberAuth(1L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUser_lastAdmin_returns400() throws Exception {
        doThrow(new ResponseStatusException(BAD_REQUEST, "Cannot delete the last admin"))
                .when(userService).softDeleteUser(2L, 2L);

        mockMvc.perform(delete("/api/v1/users/2")
                        .with(authentication(authWithPermissions(2L, "MANAGE_USERS"))))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // POST /{id}/restore  (requires MANAGE_USERS)
    // -------------------------------------------------------------------------

    @Test
    void restoreUser_withManageUsers_returns200() throws Exception {
        when(userService.restore(1L)).thenReturn(activeUser);

        mockMvc.perform(post("/api/v1/users/1/restore")
                        .with(authentication(authWithPermissions(2L, "MANAGE_USERS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void restoreUser_notDeleted_returns400() throws Exception {
        when(userService.restore(1L)).thenThrow(new ResponseStatusException(BAD_REQUEST, "User is not deleted"));

        mockMvc.perform(post("/api/v1/users/1/restore")
                        .with(authentication(authWithPermissions(2L, "MANAGE_USERS"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restoreUser_withoutPermission_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/users/1/restore")
                        .with(authentication(memberAuth(1L))))
                .andExpect(status().isForbidden());
    }
}
