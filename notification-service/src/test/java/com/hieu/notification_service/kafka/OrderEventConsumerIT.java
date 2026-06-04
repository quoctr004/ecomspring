package com.hieu.notification_service.kafka;

import com.hieu.notification_service.AbstractIntegrationTest;
import com.hieu.notification_service.repository.NotificationRepository;
import com.hieu.notification_service.service.UserProfileEmailResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;

@DisplayName("OrderEventConsumer — Kafka integration tests")
class OrderEventConsumerIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private UserProfileEmailResolver emailResolver;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll().block();
        when(emailResolver.lookupEmail(anyString())).thenReturn(Optional.empty());
    }

    private Map<String, Object> buildOrderPlacedPayload(String userId, String orderNumber) {
        return Map.of(
                "userId", userId,
                "orderNumber", orderNumber,
                "email", ""
        );
    }

    @Test
    @DisplayName("orderPlaced_createsNotification — after publishing order.placed, IN_APP notification row exists")
    void orderPlaced_createsNotification() {
        String userId = "user-" + UUID.randomUUID();
        String orderNumber = "ORD-" + UUID.randomUUID();

        kafkaTemplate.send(KafkaTopics.ORDER_PLACED, orderNumber, buildOrderPlacedPayload(userId, orderNumber));

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    var notifications = notificationRepository
                            .findByUserIdOrderByCreatedAtDescIdDesc(userId,
                                    org.springframework.data.domain.PageRequest.of(0, 10))
                            .collectList().block();
                    assertThat(notifications).isNotEmpty();
                    assertThat(notifications)
                            .anyMatch(n -> "IN_APP".equals(n.getType())
                                    && "ORDER".equals(n.getReferenceType())
                                    && orderNumber.equals(n.getReferenceId()));
                });
    }

    @Test
    @DisplayName("orderPlaced_duplicateEvent_idempotent — 2 events same orderId → 1 IN_APP notification")
    void orderPlaced_duplicateEvent_idempotent() {
        String userId = "user-" + UUID.randomUUID();
        String orderNumber = "ORD-" + UUID.randomUUID();
        var payload = buildOrderPlacedPayload(userId, orderNumber);

        kafkaTemplate.send(KafkaTopics.ORDER_PLACED, orderNumber, payload);
        kafkaTemplate.send(KafkaTopics.ORDER_PLACED, orderNumber, payload);

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    long count = notificationRepository.count().block();
                    // The unique index on (user_id, reference_type, reference_id, type)
                    // ensures only 1 IN_APP notification is persisted
                    assertThat(count).isEqualTo(1);
                    assertThat(notificationRepository
                            .findByUserIdAndReferenceTypeAndReferenceIdAndType(
                                    userId, "ORDER", orderNumber, "IN_APP")
                            .block())
                            .isNotNull();
                });
    }
}
