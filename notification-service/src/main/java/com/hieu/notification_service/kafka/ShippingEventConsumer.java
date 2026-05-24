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
 * Consumes shipping.* topics and triggers IN_APP notifications.
 * Adds EMAIL via gRPC fallback when payload doesn't carry email.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShippingEventConsumer {

    private final NotificationApplicationService notificationService;
    private final UserProfileEmailResolver emailResolver;

    @KafkaListener(topics = KafkaTopics.SHIPPING_STATUS_CHANGED, groupId = "notification-service")
    public void onShippingStatusChanged(Map<String, Object> payload) {
        var userId = str(payload, "userId");
        var orderNumber = str(payload, "orderNumber");
        var status = str(payload, "status");
        var title = "Đơn " + orderNumber + " đang ở trạng thái " + status;

        // IN_APP always. .block() on Kafka thread = at-least-once ack semantics.
        notificationService.send(SendNotificationRequest.builder()
                .userId(userId).type(NotificationType.IN_APP)
                .title(title).content(title)
                .referenceType("SHIPPING").referenceId(orderNumber)
                .build())
                .block();

        // EMAIL: payload first, gRPC fallback
        var email = str(payload, "email");
        String resolvedEmail = !email.isBlank()
                ? email
                : emailResolver.lookupEmail(userId).orElse(null);
        if (resolvedEmail != null) {
            notificationService.send(SendNotificationRequest.builder()
                    .userId(userId).type(NotificationType.EMAIL)
                    .channel(resolvedEmail).title(title).content(title)
                    .referenceType("SHIPPING").referenceId(orderNumber)
                    .build())
                    .block();
        } else {
            log.debug("No email resolved for userId={}, skipping EMAIL notification", userId);
        }
    }

    private static String str(Map<String, Object> m, String key) {
        var v = m.get(key);
        return v != null ? v.toString() : "";
    }
}
