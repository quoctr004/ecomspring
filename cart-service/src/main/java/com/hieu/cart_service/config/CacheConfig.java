package com.hieu.cart_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis cache manager wiring.
 *
 * <p>Distinct from {@link RedisConfig} (which exposes a raw template for ad-hoc
 * read/write); this one enables Spring's declarative {@code @Cacheable} layer.
 *
 * <h2>Cache naming convention</h2>
 * <ul>
 *   <li>Each {@code @Cacheable("name")} maps to a Redis key prefix
 *       {@code "<name>::<key>"}.</li>
 *   <li>TTL chosen per cache by how stale the data may be without harming UX:
 *       short TTL for prices (5 min), longer for static catalog lookups.</li>
 * </ul>
 *
 * <p>{@code null} values are NOT cached — a transport blip or fallback empty
 * response would otherwise pin a "missing" entry for the entire TTL.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Variant lookup by SKU — hot path in cart rendering. */
    public static final String CACHE_CATALOG_VARIANT = "catalog:variant";

    @Bean
    @SuppressWarnings({"deprecation", "removal"})
    public RedisCacheManager cacheManager(RedisConnectionFactory factory, ObjectMapper objectMapper) {
        // GenericJackson2JsonRedisSerializer is marked for removal in Spring Data Redis 4.x —
        // tracking issue for the project: replace with Jackson3 serializer once available.
        // Existing RedisConfig in this service uses the same class, so they migrate together.
        var defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(
                        // Variant prices change rarely; 5-minute TTL is the right trade-off
                        // between freshness and load on catalog-service during cart spikes.
                        CACHE_CATALOG_VARIANT, defaults.entryTtl(Duration.ofMinutes(5))
                ))
                .transactionAware()
                .build();
    }
}
