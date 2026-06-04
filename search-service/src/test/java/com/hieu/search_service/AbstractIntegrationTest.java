package com.hieu.search_service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base integration-test class for search-service.
 *
 * <p>Spins up Elasticsearch 9.0.0 + Kafka 7.5.0 once per JVM ({@code withReuse(true)}).
 * {@link DynamicPropertySource} wires container URIs before context refresh.
 *
 * <p>Extend this class — no extra setup required.
 * Use profile {@code test} (see application-test.yaml); Eureka disabled.
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
public abstract class AbstractIntegrationTest {

    /** Elasticsearch 9.0.0 — matches production version. */
    static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.0.0"))
                    .withEnv("discovery.type", "single-node")
                    .withEnv("xpack.security.enabled", "false");

    /** Kafka — Confluent image recognised by Testcontainers Kafka module. */
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    static {
        // Singleton lifecycle: start once per JVM, shared across all IT classes + the cached
        // Spring context. No @Testcontainers/@Container — that stops static containers after
        // the first class, breaking sibling classes that reuse the cached context. Ryuk reaps.
        ELASTICSEARCH.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", ELASTICSEARCH::getHttpHostAddress);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");
    }
}
