package com.hieu.search_service;

import org.junit.jupiter.api.Test;

/**
 * Boots the full application context. Extends {@link AbstractIntegrationTest} so the
 * context starts against real Elasticsearch + Kafka (Testcontainers) under the
 * {@code test} profile.
 *
 * <p>The previous version tried to switch infra off via
 * {@code spring.autoconfigure.exclude}, but that list included
 * {@code org.springframework.kafka.annotation.EnableKafka} — an annotation, not an
 * auto-configuration class — which Spring Boot rejects ("could not be excluded because
 * they are not auto-configuration classes"). Running against the shared containers is
 * both valid and a truer smoke test.
 */
class SearchServiceApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
