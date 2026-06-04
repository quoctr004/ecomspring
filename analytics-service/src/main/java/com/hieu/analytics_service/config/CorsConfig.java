package com.hieu.analytics_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Browser CORS. Origins come from {@code cors.allowed-origins} (comma-separated) so
 * prod/staging differ without code changes. Credentials on → wildcard origins disallowed
 * by the spec, so we keep the explicit list.
 *
 * <p>Also supplies the {@code corsFilter} bean that {@code SecurityConfig.cors(...)} relies
 * on — without it Spring Security has no {@code CorsConfigurationSource}/{@code CorsFilter}
 * to bind to and the security filter chain fails to build at startup.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter(
            @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
            List<String> allowedOrigins) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
