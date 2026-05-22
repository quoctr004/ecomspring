package com.hieu.shipping_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Single row per shipping provider. Replaces the hardcoded list previously
 * served from the controller — admin can toggle availability + reorder
 * without redeploying.
 */
@Entity
@Table(name = "shipping_carrier_config")
@Getter
@Setter
@NoArgsConstructor
public class CarrierConfigEntity {

    @Id
    @Column(length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "supports_cod", nullable = false)
    private boolean supportsCod = true;

    @Column(name = "eta_hours", nullable = false)
    private int etaHours = 48;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 100;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
