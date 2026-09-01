package com.makerspace.backend.config;

import com.makerspace.backend.security.OAuth2SuccessHandler;
import com.makerspace.backend.security.JwtAuthFilter;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    // Shared setup applied to every chain
    public HttpSecurity applySharedConfig(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class);
    }

    // Auth + OAuth2 — must be Order(1), handles login redirects
    @Bean
    @Order(1)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        applySharedConfig(http)
                .securityMatcher("/api/v1/auth/**", "/oauth2/**", "/login/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/token").permitAll()
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2SuccessHandler));
        return http.build();

    }
    // Registration — pre-register (STAFF+) and claim (any authenticated incl. ROLE_PENDING)
    @Bean
    @Order(2)
    public SecurityFilterChain registrationChain(HttpSecurity http) throws Exception {
        applySharedConfig(http)
                .securityMatcher("/api/v1/admin/registrations/**", "/api/v1/registrations/**")
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/registrations/**").hasRole("STAFF")
                        // /claim is reachable by ROLE_PENDING so pre-registered users can activate.
                        .requestMatchers(HttpMethod.POST, "/api/v1/registrations/claim").authenticated()
                        .anyRequest().hasRole("STAFF")
                );
        return http.build();
    }

    // Fallback — catches anything not claimed by a module chain
    @Bean
    @Order(100)
    public SecurityFilterChain fallbackChain(HttpSecurity http) throws Exception {
        applySharedConfig(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
