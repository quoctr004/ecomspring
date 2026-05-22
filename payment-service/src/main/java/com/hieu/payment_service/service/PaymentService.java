package com.hieu.payment_service.service;

import com.hieu.payment_service.dto.*;
import com.hieu.payment_service.entity.PaymentJpaEntity;
import com.hieu.payment_service.exception.*;
import com.hieu.payment_service.integration.momo.MomoPayUrlService;
import com.hieu.payment_service.integration.sepay.SepayQrService;
import com.hieu.payment_service.messaging.PaymentEventPublisher;
import com.hieu.payment_service.messaging.PaymentIntegrationEvents;
import com.hieu.payment_service.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String PENDING           = "PENDING";
    private static final String PAID              = "PAID";
    private static final String FAILED            = "FAILED";
    private static final String CANCELLED         = "CANCELLED";
    private static final String REFUND_REQUESTED  = "REFUND_REQUESTED";
    private static final String REFUNDED          = "REFUNDED";

    private final PaymentRepository repository;
    private final SepayQrService sepayQrService;
    private final MomoPayUrlService momoPayUrlService;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(PaymentRepository repository,
                          SepayQrService sepayQrService,
                          MomoPayUrlService momoPayUrlService,
                          ApplicationEventPublisher eventPublisher) {
        this.repository        = repository;
        this.sepayQrService    = sepayQrService;
        this.momoPayUrlService = momoPayUrlService;
        this.eventPublisher    = eventPublisher;
    }

    @Transactional
    public PaymentDTO initiatePayment(String userId, InitiatePaymentRequest req) {
        // Idempotency key check
        if (req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank()) {
            var existing = repository.findByIdempotencyKey(req.getIdempotencyKey());
            if (existing.isPresent()) {
                log.debug("Idempotent replay key={}", req.getIdempotencyKey());
                return toDTO(existing.get());
            }
        }

        // Pessimistic-lock duplicate-order guard
        if (repository.findByOrderIdWithLock(req.getOrderId()).isPresent()) {
            throw new DuplicatePaymentException(req.getOrderId());
        }

        validateMethod(req.getMethod());

        // Fold storefront aliases (SEPAY/VIETQR/BANK) into the internal enum
        // BEFORE we persist + branch on method. Without this, an order sent with
        // method="SEPAY" passes validation (alias-aware) but the QR generation
        // below misses the "BANK_TRANSFER" branch, so qrCodeUrl is null.
        String storedMethod = normalizeMethod(req.getMethod());

        PaymentJpaEntity entity = new PaymentJpaEntity();
        entity.setOrderId(req.getOrderId());
        entity.setUserId(userId);
        entity.setAmount(req.getAmount());
        entity.setCurrency(req.getCurrency() != null ? req.getCurrency() : "VND");
        entity.setMethod(storedMethod);
        entity.setStatus(PENDING);
        if (req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank()) {
            entity.setIdempotencyKey(req.getIdempotencyKey());
        }

        PaymentJpaEntity saved = repository.save(entity);
        log.debug("Initiated payment {} for order {} userId={}", saved.getId(), req.getOrderId(), userId);

        PaymentDTO.PaymentDTOBuilder builder = baseBuilder(saved);

        if ("BANK_TRANSFER".equals(storedMethod)) {
            String qrUrl = sepayQrService.generateQrUrl(req.getOrderId(), req.getAmount());
            saved.setQrCodeUrl(qrUrl);
            builder.qrCodeUrl(qrUrl)
                   .bankCode(sepayQrService.getBankCode())
                   .bankAccount(sepayQrService.getBankAccount())
                   .accountName(sepayQrService.getAccountName())
                   .transferContent(req.getOrderId());
        } else if ("MOMO".equals(storedMethod)) {
            String payUrl = momoPayUrlService.generatePayUrl(req.getOrderId(), req.getAmount());
            saved.setPayUrl(payUrl);
            builder.payUrl(payUrl);
        }

        return builder.build();
    }

    @Transactional(readOnly = true)
    public PaymentDTO getPayment(Long id, String requestingUserId, boolean isAdmin) {
        PaymentJpaEntity entity = findById(id);
        if (!isAdmin && !entity.getUserId().equals(requestingUserId)) {
            throw new PaymentAccessDeniedException(id);
        }
        return toDTO(entity);
    }

    @Transactional(readOnly = true)
    public PaymentDTO getPaymentByOrder(String orderId) {
        return toDTO(repository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId)));
    }

    @Transactional(readOnly = true)
    public PageDTO<PaymentDTO> getMyPayments(String userId, int page, int size) {
        Page<PaymentJpaEntity> result = repository.findByUserId(userId, PageRequest.of(page, size));
        return PageDTO.<PaymentDTO>builder()
                .content(result.getContent().stream().map(this::toDTO).toList())
                .pageNumber(result.getNumber())
                .pageSize(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    @Transactional
    public PaymentDTO confirmPayment(Long id, String userId, String transactionId) {
        PaymentJpaEntity entity = findById(id);
        if (!entity.getUserId().equals(userId)) {
            throw new PaymentAccessDeniedException(id);
        }
        if (!PENDING.equals(entity.getStatus())) {
            throw new InvalidPaymentStateException(
                    "Cannot confirm payment in state: " + entity.getStatus() + ". Must be PENDING.");
        }
        entity.setStatus(PAID);
        entity.setPaidAt(Instant.now());
        entity.setTransactionId(transactionId);
        PaymentJpaEntity saved = repository.save(entity);
        log.debug("Confirmed payment {} userId={}", id, userId);

        eventPublisher.publishEvent(PaymentIntegrationEvents.PaymentCompletedEvent.of(
                saved.getId(), saved.getOrderId(), saved.getUserId(),
                saved.getAmount(), saved.getCurrency(), saved.getMethod(), saved.getTransactionId()));

        return toDTO(saved);
    }

    @Transactional
    public PaymentDTO cancelPayment(Long id, String userId) {
        PaymentJpaEntity entity = findById(id);
        if (!entity.getUserId().equals(userId)) {
            throw new PaymentAccessDeniedException(id);
        }
        String status = entity.getStatus();
        if (!PENDING.equals(status) && !FAILED.equals(status)) {
            throw new InvalidPaymentStateException(
                    "Cannot cancel payment in state: " + status + ". Must be PENDING or FAILED.");
        }
        entity.setStatus(CANCELLED);
        return toDTO(repository.save(entity));
    }

    @Transactional
    public PaymentDTO requestRefund(Long id, String userId, String reason) {
        PaymentJpaEntity entity = findById(id);
        if (!entity.getUserId().equals(userId)) {
            throw new PaymentAccessDeniedException(id);
        }
        if (REFUND_REQUESTED.equals(entity.getStatus())) {
            return toDTO(entity); // idempotent
        }
        if (!PAID.equals(entity.getStatus())) {
            throw new InvalidPaymentStateException(
                    "Cannot request refund for payment in state: " + entity.getStatus() + ". Must be PAID.");
        }
        entity.setStatus(REFUND_REQUESTED);
        entity.setNotes(reason);
        return toDTO(repository.save(entity));
    }

    @Transactional
    public PaymentDTO processRefund(Long id, BigDecimal refundAmount, String reason) {
        PaymentJpaEntity entity = findById(id);
        if (REFUNDED.equals(entity.getStatus())) {
            return toDTO(entity); // idempotent
        }
        if (!REFUND_REQUESTED.equals(entity.getStatus())) {
            throw new InvalidPaymentStateException(
                    "Cannot process refund for payment in state: " + entity.getStatus()
                            + ". Must be REFUND_REQUESTED.");
        }
        BigDecimal effective = (refundAmount != null) ? refundAmount : entity.getAmount();
        if (effective.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }
        if (effective.compareTo(entity.getAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Refund amount " + effective + " exceeds original amount " + entity.getAmount());
        }
        entity.setStatus(REFUNDED);
        entity.setRefundAmount(effective);
        if (reason != null) entity.setNotes(reason);
        PaymentJpaEntity saved = repository.save(entity);
        log.debug("Processed refund for payment {} amount={}", id, effective);

        eventPublisher.publishEvent(PaymentIntegrationEvents.PaymentRefundedEvent.of(
                saved.getId(), saved.getOrderId(), saved.getUserId(),
                saved.getRefundAmount(), saved.getCurrency()));

        return toDTO(saved);
    }

    // ---- Sepay webhook auto-confirm ----

    @Transactional
    public void autoConfirmByOrderId(String orderId, String transactionId) {
        // C1: pessimistic lock prevents two concurrent webhooks double-confirming the same payment.
        repository.findByOrderIdWithLock(orderId).ifPresent(entity -> {
            if (PENDING.equals(entity.getStatus())) {
                entity.setStatus(PAID);
                entity.setPaidAt(Instant.now());
                entity.setTransactionId(transactionId);
                PaymentJpaEntity saved = repository.save(entity);
                log.info("Sepay webhook auto-confirmed payment {} order={}", saved.getId(), orderId);
                eventPublisher.publishEvent(PaymentIntegrationEvents.PaymentCompletedEvent.of(
                        saved.getId(), saved.getOrderId(), saved.getUserId(),
                        saved.getAmount(), saved.getCurrency(), saved.getMethod(), saved.getTransactionId()));
            }
        });
    }

    // ---- helpers ----

    private PaymentJpaEntity findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private static void validateMethod(String method) {
        if (method == null) throw new IllegalArgumentException("Payment method must not be null");
        try {
            PaymentMethod.valueOf(normalizeMethod(method));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid payment method: " + method);
        }
    }

    /**
     * Folds storefront-facing names into the internal enum. SEPAY is the user-
     * visible label for our bank-transfer integration (Vietnamese aggregator
     * over VietQR) so the storefront sends "SEPAY"; internally we treat it as
     * BANK_TRANSFER so the existing QR/webhook flow continues to work.
     */
    public static String normalizeMethod(String method) {
        String m = method.trim().toUpperCase();
        return switch (m) {
            case "SEPAY", "VIETQR", "BANK", "TRANSFER" -> "BANK_TRANSFER";
            default -> m;
        };
    }

    private PaymentDTO.PaymentDTOBuilder baseBuilder(PaymentJpaEntity e) {
        return PaymentDTO.builder()
                .id(e.getId())
                .orderId(e.getOrderId())
                .userId(e.getUserId())
                .amount(e.getAmount())
                .currency(e.getCurrency())
                .method(e.getMethod())
                .status(e.getStatus())
                .transactionId(e.getTransactionId())
                .gatewayResponse(e.getGatewayResponse())
                .qrCodeUrl(e.getQrCodeUrl())
                .payUrl(e.getPayUrl())
                .paidAt(e.getPaidAt())
                .refundAmount(e.getRefundAmount())
                .notes(e.getNotes())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .version(e.getVersion())
                .idempotencyKey(e.getIdempotencyKey());
    }

    private PaymentDTO toDTO(PaymentJpaEntity e) {
        return baseBuilder(e).build();
    }

    /** Allowed payment methods. */
    private enum PaymentMethod { BANK_TRANSFER, MOMO, COD }
}
