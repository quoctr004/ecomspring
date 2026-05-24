package com.hieu.notification_service.config;

import com.hieu.common.security.JwtTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive WebFlux security wiring.
 *
 * <p>Migrated from {@code @EnableWebSecurity} + {@code SecurityFilterChain}
 * (servlet) to {@code @EnableWebFluxSecurity} + {@link SecurityWebFilterChain}.
 * The {@link JwtAuthenticationFilter} runs as a {@code WebFilter} and is
 * inserted at the {@code AUTHENTICATION} position so the security context is
 * populated before authorization checks run.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    public JwtTokenValidator jwtTokenValidator(@Value("${jwt.secret}") String secret) {
        return new JwtTokenValidator(secret);
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         JwtAuthenticationFilter jwtFilter) {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeExchange(auth -> auth
                        .pathMatchers(
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/webjars/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/notifications/send")
                                .hasAnyAuthority("ROLE_ADMIN", "ROLE_SYSTEM", "ADMIN", "SYSTEM")
                        .anyExchange().authenticated())
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
