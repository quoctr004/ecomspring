package com.hieu.voucher_service.domain.voucher;

import com.hieu.voucher_service.entity.VoucherJpaEntity;
import com.hieu.voucher_service.exception.VoucherExpiredException;
import com.hieu.voucher_service.exception.VoucherMinOrderException;
import com.hieu.voucher_service.exception.VoucherUsageLimitException;
import com.hieu.voucher_service.repository.VoucherJpaRepository;
import com.hieu.voucher_service.repository.VoucherUsageRecordRepository;
import com.hieu.voucher_service.service.VoucherApplicationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for voucher discount calculation and validation.
 * No Spring context — uses Mockito to wire the service.
 */
@ExtendWith(MockitoExtension.class)
// Shared givenVoucher(...) helper stubs save() calls that validation-failure tests never
// reach (they throw before persisting). Lenient strictness keeps those shared stubs from
// being flagged as unnecessary while still exercising the real validation paths.
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Voucher domain logic (unit)")
class VoucherTest {

    @Mock VoucherJpaRepository       voucherRepo;
    @Mock VoucherUsageRecordRepository usageRepo;
    @Mock EntityManager              entityManager;

    VoucherApplicationService service;

    @BeforeEach
    void setup() {
        service = new VoucherApplicationService(voucherRepo, usageRepo, entityManager);
    }

    // ── Fixture helpers ──────────────────────────────────────────────────────────

    record VoucherSpec(String type, BigDecimal discountValue, BigDecimal maxDiscount,
                       BigDecimal minOrder, Integer usageLimit, int usedCount,
                       Instant endDate) {}

    static VoucherJpaEntity build(VoucherSpec spec) {
        var v = new VoucherJpaEntity();
        v.setCode("TEST");
        v.setType(spec.type());
        v.setDiscountValue(spec.discountValue());
        v.setMaxDiscountAmount(spec.maxDiscount());
        v.setMinOrderAmount(spec.minOrder());
        v.setUsageLimit(spec.usageLimit());
        v.setUsedCount(spec.usedCount());
        v.setActive(true);
        v.setEndDate(spec.endDate());
        return v;
    }

    void givenVoucher(VoucherJpaEntity entity) {
        when(voucherRepo.findByCodeForUpdate("TEST")).thenReturn(Optional.of(entity));
        when(voucherRepo.save(any())).thenReturn(entity);
        when(usageRepo.save(any())).thenReturn(null);
    }

    // ── Test classes ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DiscountCalculation — PERCENTAGE")
    class DiscountCalculation {

        @Test
        @DisplayName("10% trên 1,000,000 → discount = 100,000")
        void percentage_noMaxCap_correctDiscount() {
            var entity = build(new VoucherSpec(
                    "PERCENTAGE", BigDecimal.valueOf(10), null,
                    null, null, 0, null));
            givenVoucher(entity);

            var resp = service.validateAndApply(
                    "TEST", BigDecimal.valueOf(1_000_000), "u1", "o1", List.of());

            assertThat(resp.getDiscountAmount())
                    .isEqualByComparingTo(BigDecimal.valueOf(100_000));
        }

        @Test
        @DisplayName("10% trên 1,000,000 với maxDiscount=50,000 → discount = 50,000 (capped)")
        void percentage_cappedAtMaxDiscount() {
            var entity = build(new VoucherSpec(
                    "PERCENTAGE", BigDecimal.valueOf(10), BigDecimal.valueOf(50_000),
                    null, null, 0, null));
            givenVoucher(entity);

            var resp = service.validateAndApply(
                    "TEST", BigDecimal.valueOf(1_000_000), "u1", "o1", List.of());

            assertThat(resp.getDiscountAmount())
                    .isEqualByComparingTo(BigDecimal.valueOf(50_000));
        }
    }

    @Nested
    @DisplayName("FixedAmount")
    class FixedAmount {

        @Test
        @DisplayName("FIXED_AMOUNT 50,000 trên đơn 200,000 → discount = 50,000")
        void fixedAmount_smallerThanOrder_equalToValue() {
            var entity = build(new VoucherSpec(
                    "FIXED_AMOUNT", BigDecimal.valueOf(50_000), null,
                    null, null, 0, null));
            givenVoucher(entity);

            var resp = service.validateAndApply(
                    "TEST", BigDecimal.valueOf(200_000), "u1", "o1", List.of());

            assertThat(resp.getDiscountAmount())
                    .isEqualByComparingTo(BigDecimal.valueOf(50_000));
        }

        @Test
        @DisplayName("FIXED_AMOUNT lớn hơn đơn hàng → discount = order amount (không âm)")
        void fixedAmount_largerThanOrder_cappedAtOrderAmount() {
            var entity = build(new VoucherSpec(
                    "FIXED_AMOUNT", BigDecimal.valueOf(300_000), null,
                    null, null, 0, null));
            givenVoucher(entity);

            var resp = service.validateAndApply(
                    "TEST", BigDecimal.valueOf(100_000), "u1", "o1", List.of());

            assertThat(resp.getDiscountAmount())
                    .isEqualByComparingTo(BigDecimal.valueOf(100_000));
            assertThat(resp.getFinalAmount())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Validation: điều kiện không thỏa → exception")
    class Validation {

        @Test
        @DisplayName("minOrderAmount không đạt → VoucherMinOrderException")
        void minOrderAmount_notMet_throws() {
            var entity = build(new VoucherSpec(
                    "PERCENTAGE", BigDecimal.valueOf(10), null,
                    BigDecimal.valueOf(500_000), null, 0, null));
            givenVoucher(entity);

            assertThatThrownBy(() ->
                    service.validateAndApply("TEST", BigDecimal.valueOf(200_000), "u1", "o1", List.of()))
                    .isInstanceOf(VoucherMinOrderException.class);
        }

        @Test
        @DisplayName("voucher đã hết hạn → VoucherExpiredException")
        void expired_throws() {
            var entity = build(new VoucherSpec(
                    "PERCENTAGE", BigDecimal.valueOf(10), null,
                    null, null, 0,
                    Instant.now().minusSeconds(60)));  // expired 1 minute ago
            givenVoucher(entity);

            assertThatThrownBy(() ->
                    service.validateAndApply("TEST", BigDecimal.valueOf(200_000), "u1", "o1", List.of()))
                    .isInstanceOf(VoucherExpiredException.class);
        }

        @Test
        @DisplayName("usageLimit đạt rồi → VoucherUsageLimitException")
        void usageLimitReached_throws() {
            var entity = build(new VoucherSpec(
                    "PERCENTAGE", BigDecimal.valueOf(10), null,
                    null, 5, 5, null));  // limit=5, used=5
            givenVoucher(entity);

            assertThatThrownBy(() ->
                    service.validateAndApply("TEST", BigDecimal.valueOf(200_000), "u1", "o1", List.of()))
                    .isInstanceOf(VoucherUsageLimitException.class);
        }
    }

    @Nested
    @DisplayName("UsageTracking: increment và decrement usedCount")
    class UsageTracking {

        @Test
        @DisplayName("apply thành công → usedCount tăng 1")
        void apply_incrementsUsedCount() {
            var entity = build(new VoucherSpec(
                    "FIXED_AMOUNT", BigDecimal.valueOf(20_000), null,
                    null, 10, 3, null));
            givenVoucher(entity);

            service.validateAndApply("TEST", BigDecimal.valueOf(100_000), "u1", "o1", List.of());

            assertThat(entity.getUsedCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("release khi có usage record → usedCount giảm 1")
        void release_decrementsUsedCount() {
            var entity = build(new VoucherSpec(
                    "FIXED_AMOUNT", BigDecimal.valueOf(20_000), null,
                    null, 10, 4, null));
            entity.setId(1L);   // releaseVoucher matches entity.id against the usage record's voucherId

            var usageRecord = new com.hieu.voucher_service.entity.VoucherUsageRecord(
                    1L, "u1", "o1");
            when(usageRepo.findByOrderId("o1")).thenReturn(Optional.of(usageRecord));
            when(voucherRepo.findByCodeForUpdate("TEST")).thenReturn(Optional.of(entity));
            when(voucherRepo.save(any())).thenReturn(entity);

            service.releaseVoucher("TEST", "o1");

            assertThat(entity.getUsedCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("release khi không có usage record → idempotent (không throw, count không đổi)")
        void release_noUsageRecord_idempotent() {
            when(usageRepo.findByOrderId("o-missing")).thenReturn(Optional.empty());

            assertThatCode(() -> service.releaseVoucher("TEST", "o-missing"))
                    .doesNotThrowAnyException();
        }
    }
}
