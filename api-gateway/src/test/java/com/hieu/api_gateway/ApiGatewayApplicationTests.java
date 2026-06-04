package com.hieu.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test — boots the whole Spring context to catch wiring problems
 * (missing beans, bad property references, broken auto-config) before they
 * surface in production.
 *
 * <p>Eureka client is disabled here so the test doesn't try to register with
 * a service registry that isn't running in CI.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-must-be-long-enough-for-hmac-sha256-validation-padding",
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "spring.cloud.discovery.enabled=false",
        // Discovery is disabled above, so the gateway's discovery-based route locator has no
        // ReactiveDiscoveryClient to bind to — turn it off too (static routes still load).
        "spring.cloud.gateway.server.webflux.discovery.locator.enabled=false",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
        // If Spring fails to start, this test fails. No assertion needed.
    }
}
