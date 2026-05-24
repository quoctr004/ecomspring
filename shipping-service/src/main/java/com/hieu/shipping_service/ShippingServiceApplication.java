package com.hieu.shipping_service;

import com.hieu.shipping_service.config.GhtkProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** Shipping-service entry point. */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.hieu.shipping_service.rest.client")
@EnableConfigurationProperties(GhtkProperties.class)
public class ShippingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShippingServiceApplication.class, args);
    }
}
