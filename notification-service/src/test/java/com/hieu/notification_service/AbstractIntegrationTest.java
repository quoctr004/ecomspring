package com.hieu.notification_service;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for notification-service integration tests.
 *
 * <p>notification-service is reactive (WebFlux + Reactive MongoDB + Reactive Redis), so the
 * datastore is MongoDB — <b>not</b> Postgres. Spins up MongoDB + Redis + Kafka once per JVM
 * via the Testcontainers singleton pattern (started in a static initializer, shared across all
 * IT classes + the cached Spring context, reaped by Ryuk at JVM exit). The JUnit
 * {@code @Testcontainers}/{@code @Container} extension is intentionally avoided — it stops
 * static containers after the first class, which breaks sibling classes that reuse the context.
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
public abstract class AbstractIntegrationTest {

    /** MongoDB 7 — reactive document store backing notifications. */
    static final MongoDBContainer MONGODB =
            new MongoDBContainer(DockerImageName.parse("mongo:7"));

    /** Redis 7 — reactive cache / push fan-out. */
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    /** Kafka — order/payment/shipping event consumers. */
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    static {
        MONGODB.start();
        REDIS.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");
    }
}
