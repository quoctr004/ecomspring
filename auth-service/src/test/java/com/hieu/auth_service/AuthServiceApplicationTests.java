package com.hieu.auth_service;

import org.junit.jupiter.api.Test;

/**
 * Boots the full application context to catch wiring regressions.
 *
 * <p>Extends {@link AbstractIntegrationTest} so the context starts against real
 * Postgres / Redis / Kafka (Testcontainers) under the {@code test} profile — the
 * default profile points at infrastructure that isn't available in CI / local test runs.
 */
class AuthServiceApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
