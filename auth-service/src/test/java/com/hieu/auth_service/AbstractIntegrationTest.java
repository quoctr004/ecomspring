package com.hieu.auth_service;

import com.hieu.auth_service.domain.models.role.Role;
import com.hieu.auth_service.domain.models.role.vo.RoleName;
import com.hieu.auth_service.domain.repositories.RoleRepository;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests that need a full auth-service infrastructure stack.
 *
 * <p>Spins up real Postgres + Redis + Kafka via the Testcontainers <b>singleton pattern</b>:
 * the containers start once in a static initializer and are shared across every test class in
 * the JVM. Ryuk tears them down when the JVM exits. We deliberately avoid the JUnit
 * {@code @Testcontainers}/{@code @Container} extension here — it stops static containers in
 * {@code afterAll} of each class, which breaks the shared Spring context (cached across
 * sibling test classes) as soon as a second class runs and the original container is gone.
 *
 * <p>Subclasses need only extend this class; {@link DynamicPropertySource} wires the
 * containers' runtime ports into Spring properties before the context starts.
 *
 * <p>Use the {@code test} profile so {@link com.hieu.auth_service.config.DataSeeder}
 * stays inactive; tests supply their own fixtures.
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
public abstract class AbstractIntegrationTest {

    /** Postgres 16 — matches production major version. */
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("authdb")
                    .withUsername("auth")
                    .withPassword("auth");

    /** Redis 7 — backs the token blacklist. */
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    /** Kafka — Confluent image is recognised by Testcontainers' Kafka module. */
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    static {
        // Singleton lifecycle: start once for the whole JVM, never stopped explicitly
        // (Ryuk reaps on JVM exit). Shared by all subclasses + the cached Spring context.
        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
    }

    /**
     * Propagates container endpoints into Spring's Environment before context refresh.
     * Keeps Flyway + JPA aligned on the same jdbc URL (some tests verify both).
     */
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.database-platform",
                () -> "org.hibernate.dialect.PostgreSQLDialect");

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // 32-char dev secret — avoids failing JwtProperties validation in the test context.
        registry.add("jwt.secret", () -> "test-secret-test-secret-test-secret-1234");
    }

    @Autowired(required = false)
    private RoleRepository roleRepository;

    /**
     * Seeds the baseline roles that {@code DataSeeder} would create in non-test profiles.
     *
     * <p>The {@code test} profile deliberately disables {@code DataSeeder} (see class
     * javadoc) — but {@code RegisterUserHandler} still grants {@code ROLE_USER} to every
     * new user, and it can only do so if the role already exists in the registry.
     * Seeding here keeps that contract honoured for tests that assert on roles, and is
     * idempotent (the shared DB persists across tests, so a role may already be present).
     */
    @BeforeEach
    void seedDefaultRoles() {
        if (roleRepository == null) return;   // contexts without JPA repositories
        seedRole(RoleName.user(), "Default role for authenticated end users");
        seedRole(RoleName.admin(), "Administrator with full system access");
    }

    private void seedRole(RoleName name, String description) {
        if (roleRepository.findByName(name).isEmpty()) {
            roleRepository.save(Role.create(name, description));
        }
    }
}
