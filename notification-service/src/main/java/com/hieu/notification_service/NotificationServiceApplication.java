package com.hieu.notification_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

/**
 * Reactive notification-service entry point.
 *
 * <ul>
 *   <li>{@code @EnableReactiveMongoRepositories} — picks up
 *       {@code NotificationRepository} (declared {@code ReactiveMongoRepository}).</li>
 *   <li>{@code @EnableReactiveMongoAuditing} — populates {@code @CreatedDate} +
 *       {@code @LastModifiedDate} on save.</li>
 *   <li>{@code @EnableDiscoveryClient} — Eureka registration.</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableReactiveMongoRepositories(basePackages = "com.hieu.notification_service.repository")
@EnableReactiveMongoAuditing
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
