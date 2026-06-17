package com.makerspace.backend;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.security.JwtAuthFilter;
import com.makerspace.backend.services.JwtService;
import com.makerspace.backend.services.UserStateService;
import com.makerspace.backend.services.UserStateService.State;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UserStateService userStateService;
    @Mock private FilterChain chain;
    @Mock private Claims claims;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtService, userStateService);
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        return req;
    }

    /** Stubs the parts of the JWT validation common to all active-path tests. */
    private void stubTokenParseable(String token, String email) {
        when(jwtService.isValid(token)).thenReturn(true);
        when(jwtService.parseToken(token)).thenReturn(claims);
        when(claims.get("email", String.class)).thenReturn(email);
        when(claims.get("auth0Subject", String.class)).thenReturn("google-oauth2|test-subject");
    }

    // --- No token / invalid token ---

    @Test
    void noAuthHeader_passesRequestThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidToken_passesRequestThrough_withoutAuth() throws Exception {
        when(jwtService.isValid("bad.token")).thenReturn(false);
        MockHttpServletRequest req = requestWithToken("bad.token");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // --- Active user ---

    @Test
    void validToken_activeUser_populatesSecurityContext() throws Exception {
        stubTokenParseable("valid.token", "active@test.com");
        when(claims.getSubject()).thenReturn("42");
        when(claims.get("roles", List.class)).thenReturn(List.of());
        when(userStateService.stateOf("active@test.com")).thenReturn(State.ACTIVE);

        MockHttpServletRequest req = requestWithToken("valid.token");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        var principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.email()).isEqualTo("active@test.com");
        assertThat(res.getStatus()).isEqualTo(200);
    }

    // --- Soft-deleted user ---

    @Test
    void validToken_deletedUser_returns403_andDoesNotContinueChain() throws Exception {
        stubTokenParseable("valid.token", "deleted@test.com");
        when(userStateService.stateOf("deleted@test.com")).thenReturn(State.DELETED);

        MockHttpServletRequest req = requestWithToken("valid.token");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("account_closed");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // --- Unknown user ---

    @Test
    void validToken_unknownUser_returns401_andDoesNotContinueChain() throws Exception {
        stubTokenParseable("valid.token", "ghost@test.com");
        when(userStateService.stateOf("ghost@test.com")).thenReturn(State.NOT_FOUND);

        MockHttpServletRequest req = requestWithToken("valid.token");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("unknown_user");
    }

    // --- Role authorities ---

    @Test
    void validToken_activeAdminUser_setsAdminAuthority() throws Exception {
        stubTokenParseable("admin.token", "admin@test.com");
        when(claims.getSubject()).thenReturn("42");
        when(claims.get("roles", List.class)).thenReturn(List.of("ADMIN"));
        when(userStateService.stateOf("admin@test.com")).thenReturn(State.ACTIVE);

        MockHttpServletRequest req = requestWithToken("admin.token");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertThat(authorities).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void validToken_multiRoleUser_setsAllAuthorities() throws Exception {
        stubTokenParseable("multi.token", "multi@test.com");
        when(claims.getSubject()).thenReturn("7");
        when(claims.get("roles", List.class)).thenReturn(List.of("STAFF", "INSTRUCTOR"));
        when(userStateService.stateOf("multi@test.com")).thenReturn(State.ACTIVE);

        MockHttpServletRequest req = requestWithToken("multi.token");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertThat(authorities).extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_STAFF", "ROLE_INSTRUCTOR");
    }

    @Test
    void validToken_noRoles_setsEmptyAuthorities() throws Exception {
        stubTokenParseable("norole.token", "norole@test.com");
        when(claims.getSubject()).thenReturn("8");
        when(claims.get("roles", List.class)).thenReturn(List.of());
        when(userStateService.stateOf("norole@test.com")).thenReturn(State.ACTIVE);

        MockHttpServletRequest req = requestWithToken("norole.token");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertThat(authorities).isEmpty();
    }
}