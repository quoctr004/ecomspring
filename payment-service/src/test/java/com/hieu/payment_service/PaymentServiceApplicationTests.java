package com.hieu.payment_service;

import org.junit.jupiter.api.Test;

/**
 * Boots the full application context to catch wiring regressions.
 *
 * <p>Extends {@link AbstractIntegrationTest} so the context starts against real
 * Postgres + Kafka (Testcontainers) under the {@code test} profile. The previous
 * version lived in package {@code hieu.com.payment_service} — the reverse of the
 * production package {@code com.hieu.payment_service} — so {@code @SpringBootTest}
 * could not locate the {@code @SpringBootConfiguration} and the context never loaded.
 */
class PaymentServiceApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
