package com.hieu.payment_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Per-code override row for the yaml-seeded payment method catalog. The
 * storefront endpoint merges yaml defaults with these rows so admin can flip
 * {@code enabled} or re-order without touching code.
 */
@Entity
@Table(name = "payment_method_overrides")
@Getter
@Setter
@NoArgsConstructor
public class PaymentMethodOverrideEntity {

    @Id
    @Column(length = 40)
    private String code;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 100;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
