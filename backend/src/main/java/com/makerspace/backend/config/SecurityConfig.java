package com.makerspace.backend.config;

import com.makerspace.backend.model.Permission;
import com.makerspace.backend.security.JwtAuthFilter;
import com.makerspace.backend.security.OAuth2SuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.http.HttpMethod.*;

/**
 * Unified security configuration.
 *
 * Filter chains are ordered so the most specific matchers run first.
 * Access rules reference {@link Permission} constants — no role names, no
 * role hierarchy. Which roles carry a given permission is purely a database
 * concern managed through the admin API.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired private JwtAuthFilter jwtAuthFilter;
    @Autowired private OAuth2SuccessHandler oAuth2SuccessHandler;

    private HttpSecurity applyShared(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    }

    // -------------------------------------------------------------------------
    // Order 1 — Auth / OAuth2
    // -------------------------------------------------------------------------
    @Bean @Order(1)
    public SecurityFilterChain authChain(HttpSecurity http) throws Exception {
        applyShared(http)
                .securityMatcher("/api/v1/auth/**", "/oauth2/**", "/login/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/token").permitAll()
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2Login(o -> o.successHandler(oAuth2SuccessHandler));
        return http.build();
    }

    // -------------------------------------------------------------------------
    // Order 2 — Registration (invite flow)
    // REGISTER_USERS permission required for pre-registration; /claim is
    // reachable by ROLE_PENDING so pre-registered accounts can activate.
    // -------------------------------------------------------------------------
    @Bean @Order(2)
    public SecurityFilterChain registrationChain(HttpSecurity http) throws Exception {
        applyShared(http)
                .securityMatcher("/api/v1/admin/registrations/**", "/api/v1/registrations/**")
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(POST, "/api/v1/admin/registrations/**")
                            .hasAuthority(Permission.REGISTER_USERS)
                        .requestMatchers(POST, "/api/v1/registrations/claim")
                            .authenticated()    // ROLE_PENDING is sufficient
                        .anyRequest().hasAuthority(Permission.REGISTER_USERS)
                );
        return http.build();
    }

    // -------------------------------------------------------------------------
    // Order 3 — Reservations
    // -------------------------------------------------------------------------
    @Bean @Order(3)
    public SecurityFilterChain reservationChain(HttpSecurity http) throws Exception {
        applyShared(http)
                .securityMatcher("/api/v1/reservations/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(POST,  "/api/v1/reservations").authenticated()
                        .requestMatchers(GET,   "/api/v1/reservations/me/**").authenticated()
                        .requestMatchers(PATCH, "/api/v1/reservations/{id}/cancel").authenticated()
                        .requestMatchers(PATCH, "/api/v1/reservations/{id}/extend")
                            .hasAuthority(Permission.MANAGE_RESERVATIONS)
                        .requestMatchers("/api/v1/reservations/admin/all")
                            .hasAuthority(Permission.VIEW_ALL_RESERVATIONS)
                        .requestMatchers("/api/v1/reservations/admin/**")
                            .hasAuthority(Permission.VIEW_ALL_RESERVATIONS)
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    // -------------------------------------------------------------------------
    // Order 4 — Users
    // -------------------------------------------------------------------------
    @Bean @Order(4)
    public SecurityFilterChain userChain(HttpSecurity http) throws Exception {
        applyShared(http)
                .securityMatcher("/api/v1/users/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(GET,   "/api/v1/users").hasAuthority(Permission.MANAGE_USERS)
                        .requestMatchers(PATCH, "/api/v1/users/*/role").hasAuthority(Permission.MANAGE_ROLES)
                        .requestMatchers(POST,  "/api/v1/users/*/restore").hasAuthority(Permission.MANAGE_USERS)
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED)));
        return http.build();
    }

    // -------------------------------------------------------------------------
    // Order 5 — Roles / permissions admin API
    // -------------------------------------------------------------------------
    @Bean @Order(5)
    public SecurityFilterChain roleAdminChain(HttpSecurity http) throws Exception {
        applyShared(http)
                .securityMatcher("/api/v1/admin/roles/**", "/api/v1/admin/permissions/**")
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasAuthority(Permission.MANAGE_ROLES)
                );
        return http.build();
    }

    // -------------------------------------------------------------------------
    // Order 100 — Fallback
    // -------------------------------------------------------------------------
    @Bean @Order(100)
    public SecurityFilterChain fallbackChain(HttpSecurity http) throws Exception {
        applyShared(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
