package com.hieu.voucher_service.config;

import com.hieu.common.security.JwtTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
    public JwtTokenValidator jwtTokenValidator(@Value("${jwt.secret}") String secret) {
        return new JwtTokenValidator(secret);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no auth required
                .requestMatchers(HttpMethod.GET, "/api/v1/vouchers/active").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/vouchers/{id}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/vouchers/code/**").permitAll()
                // validate + release are internal saga calls — require authenticated JWT
                // so external clients can't forge release(code, orderId) to abuse vouchers
                // by un-spending them. Order-service forwards the user's JWT.
                .requestMatchers(HttpMethod.POST, "/api/v1/vouchers/validate").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/vouchers/release").authenticated()
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/actuator/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
