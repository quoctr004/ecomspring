package com.hieu.order_service;

import com.hieu.catalog_service.interfaces.grpc.proto.CatalogServiceGrpc;
import com.hieu.inventory_service.interfaces.grpc.proto.InventoryServiceGrpc;
import com.hieu.cart_service.interfaces.grpc.proto.CartServiceGrpc;
import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base IT class for order-service.
 * Spins up Postgres 16 + Redis 7 + Kafka (Confluent) once per JVM (withReuse).
 * gRPC stubs are mocked to avoid real channel connections.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.grpc.server.port=0",
        "outbox.poller.enabled=false"
})
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("orderdb")
                    .withUsername("orderuser")
                    .withPassword("orderpass");

    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    static {
        // Singleton lifecycle: start once per JVM, shared across all IT classes + the cached
        // Spring context. No @Testcontainers/@Container — that stops static containers after
        // the first class, breaking sibling classes that reuse the cached context. Ryuk reaps.
        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.database-platform",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // 32-char dev secret — keeps JwtProperties validation happy in test context
        registry.add("jwt.secret", () -> "test-secret-test-secret-test-sec1");
        registry.add("security.internal-token", () -> "test-internal-token-dev-32chars!!");
    }

    // Mock gRPC stubs so the Spring context starts without real channels.
    @MockitoBean
    CatalogServiceGrpc.CatalogServiceBlockingStub catalogStub;

    @MockitoBean
    InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    @MockitoBean
    CartServiceGrpc.CartServiceBlockingStub cartStub;
}
