package com.hieu.cart_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;

/** Entry point — Eureka registration + Feign clients enabled. */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.hieu.cart_service.rest.client")
@EnableRetry
public class CartServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
