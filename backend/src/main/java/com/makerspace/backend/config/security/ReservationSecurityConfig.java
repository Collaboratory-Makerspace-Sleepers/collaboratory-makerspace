package com.makerspace.backend.config.security;

import com.makerspace.backend.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.http.HttpMethod.*;

@Configuration
@EnableWebSecurity
public class ReservationSecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public ReservationSecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    @Order(2)
    SecurityFilterChain reservationChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/reservations/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(POST, "/api/reservations").authenticated()
                        .requestMatchers(GET, "/api/reservations/me/**").authenticated()
                        .requestMatchers(PATCH, "/api/reservations/{id}/extend").hasRole("STAFF")
                        .requestMatchers(PATCH, "/api/reservations/{id}/cancel").hasRole("STAFF")
                        .requestMatchers(PATCH, "/api/equipment/{id}/status").hasRole("STAFF")
                        .requestMatchers("/api/reservations/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .build();
    }
}