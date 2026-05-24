package com.hieu.eureka_server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP basic auth for the Eureka dashboard and {@code /eureka/*} client endpoints.
 *
 * <p>Credentials live in environment variables ({@code EUREKA_USERNAME} /
 * {@code EUREKA_PASSWORD}); dev defaults are {@code admin}/{@code admin123} so the
 * stack comes up out-of-the-box.
 *
 * <p>Actuator health + info are left unauthenticated so container orchestrators can probe
 * the instance without embedding secrets.
 *
 * <p>CSRF is disabled because Eureka clients are non-browser agents that can't participate
 * in the double-submit pattern. Keeping CSRF enabled would break registration heartbeats.
 */
@Configuration
@EnableWebSecurity
public class EurekaSecurityConfig {

    /** Hardcoded placeholder shipped in {@code application.yaml} for local dev. */
    private static final String PLACEHOLDER_PASSWORD = "admin123";

    @Value("${eureka.username:admin}")
    private String username;

    @Value("${eureka.password:admin123}")
    private String password;

    @jakarta.annotation.PostConstruct
    void rejectDefaultsInProd() {
        // A forgotten EUREKA_PASSWORD env var must crash the app — otherwise the
        // service registry (which exposes every microservice's IP) is accessible
        // with a publicly known credential.
        String profiles = System.getProperty("spring.profiles.active", "")
                + "," + System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "");
        if (profiles.contains("prod") && PLACEHOLDER_PASSWORD.equals(password)) {
            throw new IllegalStateException(
                    "Default Eureka password detected with prod profile active — " +
                    "set the EUREKA_PASSWORD environment variable");
        }
    }

    /**
     * Builds the security filter chain.
     *
     * @param http HttpSecurity builder
     * @return configured chain
     * @throws Exception propagated from Spring Security configuration
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> {});
        return http.build();
    }

    /** Single admin user loaded from env / property values. */
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder()
                .username(username)
                .password(passwordEncoder().encode(password))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    /** BCrypt — consistent with auth-service so ops can rotate a hashed pair if needed. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
