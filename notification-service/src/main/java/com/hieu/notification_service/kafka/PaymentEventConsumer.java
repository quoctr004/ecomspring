package com.hieu.notification_service.kafka;

import com.hieu.notification_service.dto.SendNotificationRequest;
import com.hieu.notification_service.entity.NotificationType;
import com.hieu.notification_service.service.NotificationApplicationService;
import com.hieu.notification_service.service.UserProfileEmailResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes payment.* topics and triggers notifications.
 * Falls back to gRPC email lookup when payload doesn't carry email.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private static final String REF_TYPE_PAYMENT = "PAYMENT";


    private final NotificationApplicationService notificationService;
    private final UserProfileEmailResolver emailResolver;

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED, groupId = "notification-service")
    public void onPaymentCompleted(Map<String, Object> payload) {
        var userId = str(payload, "userId");
        var amount = str(payload, "amount");
        var title = "Thanh toán " + amount + " VND thành công";
        var content = "Thanh toán " + amount + " VND đã được xử lý thành công.";
        var paymentId = str(payload, "paymentId");
        var email = str(payload, "email");

        // IN_APP — .block() acceptable on Kafka consumer thread (not a request thread);
        // lets the broker ack only after persistence succeeds (at-least-once).
        notificationService.send(SendNotificationRequest.builder()
                .userId(userId).type(NotificationType.IN_APP)
                .title(title).content(content)
                .referenceType(REF_TYPE_PAYMENT).referenceId(paymentId)
                .build())
                .block();

        // EMAIL: payload first, gRPC fallback
        String resolvedEmail = !email.isBlank()
                ? email
                : emailResolver.lookupEmail(userId).orElse(null);
        if (resolvedEmail != null) {
            notificationService.send(SendNotificationRequest.builder()
                    .userId(userId).type(NotificationType.EMAIL)
                    .channel(resolvedEmail).title(title).content(content)
                    .referenceType(REF_TYPE_PAYMENT).referenceId(paymentId)
                    .build())
                    .block();
        } else {
            log.debug("No email resolved for userId={}, skipping EMAIL notification", userId);
        }
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "notification-service")
    public void onPaymentFailed(Map<String, Object> payload) {
        var userId = str(payload, "userId");
        var title = "Thanh toán thất bại";
        var content = "Giao dịch thanh toán của bạn không thành công. Vui lòng thử lại.";
        var paymentId = str(payload, "paymentId");

        notificationService.send(SendNotificationRequest.builder()
                .userId(userId).type(NotificationType.IN_APP)
                .title(title).content(content)
                .referenceType(REF_TYPE_PAYMENT).referenceId(paymentId)
                .build())
                .block();
    }

    private static String str(Map<String, Object> m, String key) {
        var v = m.get(key);
        return v != null ? v.toString() : "";
    }
}
