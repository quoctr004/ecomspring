package com.hieu.order_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.order_service.domain.exception.ServiceUnavailableException;
import com.hieu.order_service.infrastructure.rest.client.exception.VoucherInvalidException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit test of {@link FeignConfig.OrderFeignErrorDecoder}.
 *
 * <p>This is the crown jewel of the Feign migration — it decides whether a 4xx
 * response is a business rejection (saga → FAILED) or a transport-style failure
 * (saga → compensation). Bugs here cause silent loss of money (wrong refund
 * triggered, voucher double-released, etc.) so we cover the decision matrix
 * explicitly.
 *
 * <p>No Spring context, no WireMock — we exercise the decoder by handing it
 * synthetic {@link Response} objects. Sub-second test suite, low maintenance.
 */
class OrderFeignErrorDecoderTest {

    private ErrorDecoder decoder;

    @BeforeEach
    void setUp() {
        decoder = new FeignConfig.OrderFeignErrorDecoder(new ObjectMapper());
    }

    @Test
    @DisplayName("VoucherClient#validate + 4xx → VoucherInvalidException with parsed message")
    void voucherValidate_4xx_throwsVoucherInvalid() {
        Response response = responseWith(422, """
                {"success":false,"code":"VOUCHER-EXPIRED","message":"voucher expired"}
                """);

        Exception ex = decoder.decode("VoucherClient#validate(ValidateRequest)", response);

        assertThat(ex).isInstanceOf(VoucherInvalidException.class)
                .hasMessageContaining("voucher expired");
    }

    @Test
    @DisplayName("VoucherClient#validate + 4xx with empty body → VoucherInvalidException with default msg")
    void voucherValidate_4xx_emptyBody_defaultMessage() {
        Response response = responseWith(404, "");

        Exception ex = decoder.decode("VoucherClient#validate(ValidateRequest)", response);

        assertThat(ex).isInstanceOf(VoucherInvalidException.class)
                .hasMessageContaining("voucher rejected");
    }

    @Test
    @DisplayName("VoucherClient#validate + 5xx → ServiceUnavailableException (NOT VoucherInvalid)")
    void voucherValidate_5xx_throwsUnavailable() {
        Response response = responseWith(503, "");

        Exception ex = decoder.decode("VoucherClient#validate(ValidateRequest)", response);

        assertThat(ex).isInstanceOf(ServiceUnavailableException.class)
                .isNotInstanceOf(VoucherInvalidException.class);
    }

    @Test
    @DisplayName("VoucherClient#release + 4xx → ServiceUnavailableException (NOT VoucherInvalid — release is idempotent compensation)")
    void voucherRelease_4xx_doesNotMapToInvalid() {
        Response response = responseWith(404, "");

        Exception ex = decoder.decode("VoucherClient#release(ReleaseRequest)", response);

        assertThat(ex).isInstanceOf(ServiceUnavailableException.class)
                .isNotInstanceOf(VoucherInvalidException.class);
    }

    @Test
    @DisplayName("PaymentClient#initiate + 4xx → ServiceUnavailableException (treat as transport)")
    void paymentInitiate_4xx_throwsUnavailable() {
        Response response = responseWith(400, "");

        Exception ex = decoder.decode("PaymentClient#initiate(InitiateRequest,String)", response);

        assertThat(ex).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("InventoryClient#reserveStock + 500 → ServiceUnavailableException")
    void inventoryReserve_5xx_throwsUnavailable() {
        Response response = responseWith(500, "");

        Exception ex = decoder.decode("InventoryClient#reserveStock(ReserveRequest)", response);

        assertThat(ex).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("Body with no 'message' field → falls back to default rejection message")
    void voucherValidate_bodyWithoutMessage_fallsBackToDefault() {
        Response response = responseWith(422, "{\"success\":false,\"code\":\"X\"}");

        Exception ex = decoder.decode("VoucherClient#validate(ValidateRequest)", response);

        assertThat(ex).isInstanceOf(VoucherInvalidException.class)
                .hasMessageContaining("voucher rejected");
    }

    @Test
    @DisplayName("Malformed JSON body is tolerated (returns default message)")
    void voucherValidate_malformedJson_tolerated() {
        Response response = responseWith(422, "{not-json");

        Exception ex = decoder.decode("VoucherClient#validate(ValidateRequest)", response);

        // Should not throw an IOException — the decoder swallows parse errors and
        // returns the exception type appropriate for status + methodKey.
        assertThat(ex).isInstanceOf(VoucherInvalidException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Response responseWith(int status, String body) {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "http://test/api/v1/vouchers/validate",
                Collections.emptyMap(),
                new byte[0],
                StandardCharsets.UTF_8,
                new RequestTemplate());

        return Response.builder()
                .status(status)
                .reason("test")
                .request(request)
                .body(body == null ? null : body.getBytes(StandardCharsets.UTF_8))
                .build();
    }
}
