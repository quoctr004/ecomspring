package com.hieu.user_profile_service;

import org.junit.jupiter.api.Test;

/**
 * Boots the full application context. Extends {@link AbstractIntegrationTest} so the
 * context starts against real Postgres + Kafka (Testcontainers) under the {@code test}
 * profile — the test config supplies no datasource of its own, so without the shared
 * containers the context would fall back to the (unavailable) localhost database.
 */
class UserProfileServiceApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
