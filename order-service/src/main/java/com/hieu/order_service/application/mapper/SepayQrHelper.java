package com.hieu.order_service.application.mapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;

/**
 * Builds the SePay VietQR URL from order data alone, without calling
 * payment-service. Used by {@link OrderDtoMapper} when reading an existing
 * PAYMENT_PENDING order — the saga only produces the QR on the create
 * response, so re-fetching the order later would otherwise lose the URL and
 * the storefront would render an empty placeholder.
 *
 * <p>Bank config (account / bank code) must mirror payment-service's
 * {@code SepayQrService} or the user will scan a QR whose destination doesn't
 * match the account watched by the webhook.
 */
@Component
public class SepayQrHelper {

    @Value("${sepay.qr-base-url:https://qr.sepay.vn/img}")
    private String qrBaseUrl;

    @Value("${sepay.bank-code:BIDV}")
    private String bankCode;

    @Value("${sepay.bank-account:96247CISI1}")
    private String bankAccount;

    public String generate(String orderNumber, BigDecimal amount) {
        if (orderNumber == null || amount == null) return null;
        return UriComponentsBuilder.fromUriString(qrBaseUrl)
                .queryParam("acc", bankAccount)
                .queryParam("bank", bankCode)
                .queryParam("amount", amount.toPlainString())
                .queryParam("des", orderNumber)
                .toUriString();
    }

    /** Whether this payment method should produce a SePay QR. */
    public boolean isBankTransfer(String paymentMethod) {
        if (paymentMethod == null) return false;
        String m = paymentMethod.trim().toUpperCase();
        return m.equals("SEPAY") || m.equals("BANK_TRANSFER") || m.equals("VIETQR") || m.equals("BANK");
    }
}
