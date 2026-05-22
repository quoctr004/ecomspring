package com.hieu.shipping_service;

import com.hieu.shipping_service.config.GhtkProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Shipping-service entry point. */
@SpringBootApplication
@EnableConfigurationProperties(GhtkProperties.class)
public class ShippingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShippingServiceApplication.class, args);
    }
}
