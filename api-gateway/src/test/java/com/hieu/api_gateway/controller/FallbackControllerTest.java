package com.hieu.api_gateway.controller;

import com.hieu.common.api.ApiResponse;
import com.hieu.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the circuit-breaker fallback endpoint.
 *
 * <p>Plain JUnit — no Spring context needed because the controller is a pure
 * function (no injected dependencies). Faster than a slice test and avoids
 * the webflux autoconfig that a gateway-only project doesn't pull in.
 */
class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    @Test
    @DisplayName("fallback() returns 503 Service Unavailable")
    void fallback_returnsServiceUnavailable() {
        ResponseEntity<ApiResponse<Void>> response = controller.fallback();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("fallback() body carries SERVICE_UNAVAILABLE error code")
    void fallback_bodyHasServiceUnavailableErrorCode() {
        ResponseEntity<ApiResponse<Void>> response = controller.fallback();

        ApiResponse<Void> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.code());
    }

    @Test
    @DisplayName("fallback() body has a human-readable message (clients show it as a toast)")
    void fallback_bodyHasHumanReadableMessage() {
        ResponseEntity<ApiResponse<Void>> response = controller.fallback();

        assertThat(response.getBody().message())
                .isNotBlank()
                .contains("temporarily unavailable");
    }

    @Test
    @DisplayName("fallback() data is null — no payload for error responses")
    void fallback_bodyHasNoData() {
        ResponseEntity<ApiResponse<Void>> response = controller.fallback();

        assertThat(response.getBody().data()).isNull();
    }
}
