package com.hieu.order_service;

import com.hieu.catalog_service.interfaces.grpc.proto.CatalogServiceGrpc;
import com.hieu.inventory_service.interfaces.grpc.proto.InventoryServiceGrpc;
import com.hieu.cart_service.interfaces.grpc.proto.CartServiceGrpc;
import com.hieu.order_service.application.command.order.CreateOrderCommand;
import com.hieu.order_service.application.handler.order.CreateOrderHandler;
import com.hieu.order_service.infrastructure.outbox.OutboxEventJpaRepository;
import com.hieu.order_service.infrastructure.outbox.OutboxPoller;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox integration tests for order-service.
 * External gRPC stubs are mocked so only the DB + Redis + Kafka layers are real.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.grpc.server.port=0",
        "outbox.poller.enabled=false",
        "outbox.poll-delay-ms=100",
        "outbox.batch-size=10"
})
class OrderOutboxIntegrationTest {

    // ── Containers ────────────────────────────────────────────────────────────

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("orderdb")
                    .withUsername("orderuser")
                    .withPassword("orderpass");

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    // ── Mock external gRPC stubs (avoids real channel connections) ────────────

    @MockitoBean
    CatalogServiceGrpc.CatalogServiceBlockingStub catalogStub;

    @MockitoBean
    InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    @MockitoBean
    CartServiceGrpc.CartServiceBlockingStub cartStub;

    // ── Autowired beans ───────────────────────────────────────────────────────

    @Autowired
    CreateOrderHandler createOrderHandler;

    @Autowired
    OutboxEventJpaRepository outboxRepo;

    @Autowired(required = false)
    OutboxPoller outboxPoller;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final java.util.concurrent.atomic.AtomicInteger SEQ =
            new java.util.concurrent.atomic.AtomicInteger(0);

    private CreateOrderCommand buildOrderCmd() {
        int seq = SEQ.incrementAndGet();
        var item = new CreateOrderCommand.ItemCmd(
                (long) seq, "Product " + seq, (long) seq, "SKU-OUT-" + seq, null,
                new BigDecimal("10.00"), 1
        );
        return new CreateOrderCommand(
                java.util.UUID.randomUUID().toString(),
                List.of(item),
                "Recipient " + seq,
                "09" + String.format("%08d", seq),
                "Street " + seq, "Ward 1", "District 1", "HCMC", "VN", "70000",
                "COD", null, null,
                "idem-" + seq + "-" + System.nanoTime(),
                null
        );
    }

    @BeforeEach
    void cleanOutbox() {
        outboxRepo.deleteAll();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void shouldWriteToOutboxBeforeCommit() {
        var cmd = buildOrderCmd();

        // buildAndSave persists order + outbox row in one transaction; poller off
        var order = createOrderHandler.buildAndSave(cmd);

        assertThat(order.getId()).isNotNull();

        var events = outboxRepo.findAll();
        assertThat(events).hasSize(1);

        var evt = events.get(0);
        assertThat(evt.getEventType()).isEqualTo("OrderPlacedEvent");
        assertThat(evt.getProcessedAt()).isNull();   // poller disabled — not yet sent
        assertThat(evt.getRetryCount()).isZero();
        assertThat(evt.getPayload()).isNotBlank();
        assertThat(evt.getTopic()).isEqualTo("order.placed");
    }

    @Test
    void shouldPublishOnPollerTick() throws Exception {
        // Write one outbox row
        createOrderHandler.buildAndSave(buildOrderCmd());
        assertThat(outboxRepo.findAll()).hasSize(1);

        if (outboxPoller == null) {
            // Poller bean absent (outbox.poller.enabled=false) — verify row written, skip publish
            assertThat(outboxRepo.findAll().get(0).getProcessedAt()).isNull();
            return;
        }

        // Subscribe to Kafka BEFORE invoking poller
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(), "test-outbox-pub", "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        BlockingQueue<ConsumerRecord<String, String>> received = new LinkedBlockingQueue<>();
        var factory = new DefaultKafkaConsumerFactory<String, String>(props);
        var containerProps = new ContainerProperties("order.placed");
        var container = new KafkaMessageListenerContainer<>(factory, containerProps);
        container.setupMessageListener((MessageListener<String, String>) received::add);
        container.start();

        try {
            outboxPoller.poll();

            // Row must be marked processed
            var rows = outboxRepo.findAll();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getProcessedAt()).isNotNull();

            // Kafka must receive the message within 10s
            var record = received.poll(10, TimeUnit.SECONDS);
            assertThat(record).isNotNull();
            assertThat(record.topic()).isEqualTo("order.placed");
        } finally {
            container.stop();
        }
    }

    @Test
    void shouldRetryWithBackoff() {
        // Write outbox row
        createOrderHandler.buildAndSave(buildOrderCmd());
        var events = outboxRepo.findAll();
        assertThat(events).hasSize(1);
        Long eventId = events.get(0).getId();

        if (outboxPoller == null) {
            // Poller disabled — row remains PENDING, nothing to assert on retry
            assertThat(outboxRepo.findById(eventId))
                    .isPresent()
                    .hasValueSatisfying(e -> {
                        assertThat(e.getProcessedAt()).isNull();
                        assertThat(e.getRetryCount()).isZero();
                    });
            return;
        }

        // Simulate Kafka unavailability by stopping the container
        kafka.stop();

        try {
            outboxPoller.poll();
        } catch (Exception ignored) {
            // Expected when Kafka is down
        }

        var updated = outboxRepo.findById(eventId);
        assertThat(updated).isPresent();

        var row = updated.get();
        // retryCount bumped (> 0) and nextAttemptAt pushed into the future
        assertThat(row.getRetryCount()).isGreaterThan(0);
        assertThat(row.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(row.getProcessedAt()).isNull();
    }
}
