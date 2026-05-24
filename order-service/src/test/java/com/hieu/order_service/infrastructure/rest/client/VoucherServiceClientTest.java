package com.hieu.order_service.infrastructure.rest.client;

import com.hieu.common.api.ApiResponse;
import com.hieu.order_service.domain.exception.ServiceUnavailableException;
import com.hieu.order_service.infrastructure.rest.client.exception.VoucherInvalidException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Facade-level unit tests for {@link VoucherServiceClient}.
 *
 * <p>Key behaviours covered:
 * <ul>
 *   <li>{@code validateAndApply} returns the discount amount on success.</li>
 *   <li>{@code validateAndApply} re-throws {@link VoucherInvalidException}
 *       untouched so saga can map to order FAILED with the real reason.</li>
 *   <li>Empty / missing discount → {@link ServiceUnavailableException}.</li>
 *   <li>{@code release} never throws — compensation is best-effort with Kafka
 *       backstop. Both {@code ServiceUnavailableException} (from the
 *       error-decoder) and raw {@link FeignException} are swallowed.</li>
 *   <li>productId list is correctly converted to Strings (server contract).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class VoucherServiceClientTest {

    @Mock VoucherClient voucherClient;

    @InjectMocks VoucherServiceClient facade;

    @Test
    @DisplayName("validateAndApply: returns discount amount on success")
    void validateAndApply_returnsDiscount() {
        var data = new VoucherClient.ValidateResponse(BigDecimal.valueOf(50_000), "PERCENTAGE");
        when(voucherClient.validate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ApiResponse<>(true, "OK", null, data, Instant.now(), null));

        BigDecimal discount = facade.validateAndApply(
                "SUMMER", BigDecimal.valueOf(500_000), "user-1", 100L, List.of(1L, 2L), null);

        assertThat(discount).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("validateAndApply: VoucherInvalidException (from ErrorDecoder) is re-thrown unchanged")
    void validateAndApply_rejected_rethrows() {
        when(voucherClient.validate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new VoucherInvalidException("voucher expired", null));

        assertThatThrownBy(() -> facade.validateAndApply(
                "EXPIRED", BigDecimal.TEN, "user-1", 1L, List.of(), null))
                .isInstanceOf(VoucherInvalidException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("validateAndApply: missing discountAmount → ServiceUnavailableException")
    void validateAndApply_missingDiscount_throws() {
        var data = new VoucherClient.ValidateResponse(null, null);
        when(voucherClient.validate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ApiResponse<>(true, "OK", null, data, Instant.now(), null));

        assertThatThrownBy(() -> facade.validateAndApply(
                "BROKEN", BigDecimal.TEN, "user-1", 1L, List.of(), null))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("validateAndApply: raw FeignException → ServiceUnavailableException")
    void validateAndApply_feignError_maps() {
        when(voucherClient.validate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(feignError(503));

        assertThatThrownBy(() -> facade.validateAndApply(
                "X", BigDecimal.TEN, "u", 1L, List.of(), null))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("validateAndApply: productIds are encoded as Strings to match server DTO")
    void validateAndApply_productIdsAreStrings() {
        var data = new VoucherClient.ValidateResponse(BigDecimal.ZERO, "FLAT");
        when(voucherClient.validate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ApiResponse<>(true, "OK", null, data, Instant.now(), null));

        facade.validateAndApply("X", BigDecimal.TEN, "u", 42L, List.of(10L, 20L), null);

        ArgumentCaptor<VoucherClient.ValidateRequest> captor =
                ArgumentCaptor.forClass(VoucherClient.ValidateRequest.class);
        verify(voucherClient).validate(captor.capture());

        var req = captor.getValue();
        assertThat(req.orderId()).isEqualTo("42");
        assertThat(req.productIds()).containsExactly("10", "20");
    }

    @Test
    @DisplayName("release: swallows ServiceUnavailableException (Kafka backstop covers it)")
    void release_swallowsUnavailable() {
        org.mockito.Mockito.doThrow(new ServiceUnavailableException("voucher-service"))
                .when(voucherClient).release(org.mockito.ArgumentMatchers.any());

        assertThatCode(() -> facade.release("CODE", 100L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("release: swallows raw FeignException (network I/O)")
    void release_swallowsFeignException() {
        org.mockito.Mockito.doThrow(feignError(500))
                .when(voucherClient).release(org.mockito.ArgumentMatchers.any());

        assertThatCode(() -> facade.release("CODE", 100L)).doesNotThrowAnyException();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static FeignException feignError(int status) {
        Request req = Request.create(Request.HttpMethod.POST, "http://stub", Collections.emptyMap(),
                new byte[0], StandardCharsets.UTF_8, new RequestTemplate());
        return FeignException.errorStatus("stub", feign.Response.builder()
                .status(status)
                .request(req)
                .reason("stubbed")
                .build());
    }
}
