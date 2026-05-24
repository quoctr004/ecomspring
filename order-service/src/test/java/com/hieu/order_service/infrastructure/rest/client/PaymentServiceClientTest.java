package com.hieu.order_service.infrastructure.rest.client;

import com.hieu.common.api.ApiResponse;
import com.hieu.order_service.domain.exception.ServiceUnavailableException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Facade-level unit tests for {@link PaymentServiceClient}.
 *
 * <p>{@link PaymentClient} is mocked — we're verifying the wrapper's behaviour
 * (response unwrap, FeignException → {@link ServiceUnavailableException},
 * two-step refund flow). Real HTTP behaviour is covered separately by the
 * Feign error-decoder integration test.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceClientTest {

    @Mock PaymentClient paymentClient;

    @InjectMocks PaymentServiceClient facade;

    private static final String ORDER_ID = "ord-123";
    private static final String ADMIN_TOKEN = "admin-token";

    @Test
    @DisplayName("initiate(): unwraps ApiResponse and returns PaymentInitiated")
    void initiate_unwrapsResponse() {
        var data = new PaymentClient.PaymentInitiated(42L, "qr://abc", "https://pay/123");
        when(paymentClient.initiate(any(), any()))
                .thenReturn(new ApiResponse<>(true, "OK", null, data, Instant.now(), null));

        var result = facade.initiate(ORDER_ID, BigDecimal.TEN, "sepay", "idem-1", null);

        assertThat(result.paymentId()).isEqualTo(42L);
        assertThat(result.qrCodeUrl()).isEqualTo("qr://abc");
        assertThat(result.payUrl()).isEqualTo("https://pay/123");
    }

    @Test
    @DisplayName("initiate(): null response data → ServiceUnavailableException")
    void initiate_nullData_throwsUnavailable() {
        when(paymentClient.initiate(any(), any()))
                .thenReturn(new ApiResponse<>(true, "OK", null, null, Instant.now(), null));

        assertThatThrownBy(() -> facade.initiate(ORDER_ID, BigDecimal.TEN, "sepay", null, null))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("initiate(): FeignException → ServiceUnavailableException (saga compensation cue)")
    void initiate_feignException_translatedToUnavailable() {
        when(paymentClient.initiate(any(), any()))
                .thenThrow(feignError(503));

        assertThatThrownBy(() -> facade.initiate(ORDER_ID, BigDecimal.TEN, "sepay", null, null))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("processRefundForOrder(): no payment found → no-op (no refund call)")
    void refund_noPayment_isNoOp() {
        when(paymentClient.getPaymentByOrderId(ORDER_ID))
                .thenReturn(new ApiResponse<>(true, "OK", null, null, Instant.now(), null));

        facade.processRefundForOrder(ORDER_ID, BigDecimal.valueOf(100), ADMIN_TOKEN);

        verify(paymentClient).getPaymentByOrderId(ORDER_ID);
        // Refund endpoint must NOT be called when no payment exists.
        verify(paymentClient, org.mockito.Mockito.never())
                .processRefund(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    @DisplayName("processRefundForOrder(): payment exists → invokes processRefund with correct id")
    void refund_paymentExists_callsRefund() {
        var payment = new PaymentClient.Payment(99L, ORDER_ID, BigDecimal.valueOf(100), "COMPLETED");
        when(paymentClient.getPaymentByOrderId(ORDER_ID))
                .thenReturn(new ApiResponse<>(true, "OK", null, payment, Instant.now(), null));
        when(paymentClient.processRefund(org.mockito.ArgumentMatchers.eq(99L), any()))
                .thenReturn(new ApiResponse<>(true, "OK", null, null, Instant.now(), null));

        facade.processRefundForOrder(ORDER_ID, BigDecimal.valueOf(100), ADMIN_TOKEN);

        verify(paymentClient).processRefund(org.mockito.ArgumentMatchers.eq(99L),
                any(PaymentClient.RefundRequest.class));
    }

    @Test
    @DisplayName("processRefundForOrder(): lookup throws Feign error → ServiceUnavailableException")
    void refund_lookupFails_throwsUnavailable() {
        when(paymentClient.getPaymentByOrderId(ORDER_ID))
                .thenThrow(feignError(500));

        assertThatThrownBy(() -> facade.processRefundForOrder(ORDER_ID, BigDecimal.TEN, ADMIN_TOKEN))
                .isInstanceOf(ServiceUnavailableException.class);

        // Refund endpoint must NOT be called when lookup itself failed.
        verify(paymentClient, org.mockito.Mockito.never())
                .processRefund(org.mockito.ArgumentMatchers.anyLong(), any());
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
