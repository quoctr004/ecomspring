package com.hieu.auth_service.config;

import com.hieu.auth_service.infrastructure.security.CustomUserDetailsService;
import com.hieu.auth_service.interfaces.rest.filter.JwtAuthenticationEntryPoint;
import com.hieu.auth_service.interfaces.rest.filter.JwtAuthenticationFilter;
import com.hieu.auth_service.interfaces.rest.filter.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security chain.
 *
 * <p>Architecture:
 * <ul>
 *   <li><b>UserDetails path</b>: {@link CustomUserDetailsService} loads a rich
 *       {@link com.hieu.auth_service.infrastructure.security.AuthUserDetails} from the
 *       domain via repositories. Wired through {@link DaoAuthenticationProvider} so
 *       {@link AuthenticationManager} stays usable for future flows / tests.</li>
 *   <li><b>Request filter</b>: {@link JwtAuthenticationFilter} runs
 *       before {@link UsernamePasswordAuthenticationFilter}; it parses the bearer
 *       token, checks the blacklist, enforces {@code tokenVersion}, rehydrates the
 *       principal via {@code UserDetailsService}, and sets the SecurityContext.</li>
 *   <li><b>Entry point</b>: {@link JwtAuthenticationEntryPoint} emits a JSON 401
 *       matching the global error schema.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // replaces deprecated @EnableGlobalMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final RateLimitFilter rateLimitFilter;

    /**
     * Dev-only chain (non-prod profiles): permits Swagger UI, OpenAPI docs, and H2 console.
     * Matched first (@Order(1)) so these paths never reach the main security chain.
     * WARN: do NOT activate the "prod" profile in development — these endpoints are unprotected.
     */
    @Bean
    @Order(1)
    @Profile("!prod")
    public SecurityFilterChain devEndpoints(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/h2-console/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(h -> h.frameOptions(f -> f.sameOrigin())); // required for H2 console iframe
        return http.build();
    }

    /**
     * Builds the stateless security chain.
     *
     * <p>Filter order: JWT filter first → standard username/password filter → the rest.
     * Sessions are disabled — every call must carry credentials or a valid JWT.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        // Spring Security 7: ERROR dispatch must be explicitly permitted, otherwise
                        // any forwarded /error path falls back to anyRequest().authenticated() → 401.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        // Public auth endpoints
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/google",
                                "/api/v1/auth/refresh").permitAll()
                        // C2: Only health+info are public; all other actuator endpoints require ROLE_ADMIN.
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("ROLE_ADMIN")
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                // C5: Rate limiter runs before JWT filter so throttled requests never reach auth logic.
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * {@link DaoAuthenticationProvider} wired to our custom {@link CustomUserDetailsService}
     * and BCrypt encoder. Active whenever {@link AuthenticationManager} is used.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /** Exposes the standard {@link AuthenticationManager} for programmatic authentication. */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /** Single source of truth for password hashing; {@code BCryptPasswordEncoderAdapter} delegates here. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // M2: Bump cost from default 10 to 12 — OWASP minimum for BCrypt.
        return new BCryptPasswordEncoder(12);
    }
}
