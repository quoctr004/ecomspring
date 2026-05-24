package com.hieu.shipping_service.config;

import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared Feign config for shipping-service.
 *
 * <p>Timeouts and log level are externalised to {@code application.yaml} under
 * {@code spring.cloud.openfeign.client.config.*} (with a {@code ghtk} block that
 * bumps timeouts for the slower 3rd-party API). Only the retry policy stays in
 * code because Spring Cloud picks up a {@link Retryer} bean automatically.
 *
 * <p>No JWT propagation interceptor — GHTK uses a raw token header (not Bearer),
 * and {@code OrderClient} runs from a Kafka consumer thread where the servlet
 * request context is absent. Each client supplies the headers it needs at the
 * method signature.
 *
 * <p>{@link Retryer#NEVER_RETRY} — GHTK's fee endpoint is GET so retry would be
 * safe, but the local fallback already handles outages; an extra retry just
 * delays the user without changing the outcome.
 */
@Configuration
public class FeignConfig {

    @Bean
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
