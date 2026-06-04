package com.hieu.notification_service.service;

import com.hieu.notification_service.AbstractIntegrationTest;
import com.hieu.notification_service.dto.NotificationDTO;
import com.hieu.notification_service.dto.SendNotificationRequest;
import com.hieu.notification_service.entity.NotificationType;
import com.hieu.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationApplicationService — integration tests")
class NotificationApplicationServiceIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationApplicationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    // Mock gRPC-backed resolver — not needed for IN_APP notifications
    @MockitoBean
    private UserProfileEmailResolver emailResolver;

    @BeforeEach
    void cleanDb() {
        notificationRepository.deleteAll().block();
    }

    private SendNotificationRequest buildRequest(String userId, String refId) {
        return SendNotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.IN_APP)
                .title("Test Notification")
                .content("Test content for " + refId)
                .referenceType("ORDER")
                .referenceId(refId)
                .build();
    }

    @Test
    @DisplayName("send_persistsRow — single call creates exactly 1 notification row")
    void send_persistsRow() {
        String userId = "user-" + UUID.randomUUID();
        String refId = "ORD-" + UUID.randomUUID();

        NotificationDTO dto = notificationService.send(buildRequest(userId, refId)).block();

        assertThat(dto).isNotNull();
        assertThat(dto.userId()).isEqualTo(userId);

        long count = notificationRepository.count().block();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("send_duplicateEvent_idempotent — same userId+refType+refId+type → 1 row, no exception")
    void send_duplicateEvent_idempotent() {
        String userId = "user-" + UUID.randomUUID();
        String refId = "ORD-" + UUID.randomUUID();
        var req = buildRequest(userId, refId);

        NotificationDTO first = notificationService.send(req).block();
        NotificationDTO second = notificationService.send(req).block(); // must not throw

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();

        long count = notificationRepository.count().block();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("send_concurrentDuplicate_serializes — 3 threads same payload → final 1 row")
    void send_concurrentDuplicate_serializes() throws InterruptedException {
        String userId = "user-" + UUID.randomUUID();
        String refId = "ORD-" + UUID.randomUUID();
        var req = buildRequest(userId, refId);

        int threads = 3;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    notificationService.send(req).block();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        long count = notificationRepository.count().block();
        assertThat(count).isEqualTo(1);
        assertThat(successCount.get() + errorCount.get()).isEqualTo(threads);
    }
}
