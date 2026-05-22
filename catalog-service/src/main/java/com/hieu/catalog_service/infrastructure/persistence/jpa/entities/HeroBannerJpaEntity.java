package com.hieu.catalog_service.infrastructure.persistence.jpa.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Homepage hero slideshow row. Image URLs are stored as external strings —
 * upload pipeline lives in the FE (or an asset host); BE only owns metadata.
 *
 * <p>Storefront query: {@code enabled = true} AND (start_at IS NULL OR
 * start_at &lt;= NOW) AND (end_at IS NULL OR end_at &gt; NOW), sorted by
 * displayOrder ASC. Admin sees the unfiltered list.
 */
@Entity
@Table(name = "hero_banners")
@Getter
@Setter
@NoArgsConstructor
public class HeroBannerJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String subtitle;

    @Column(name = "image_url", nullable = false, length = 1024)
    private String imageUrl;

    @Column(name = "target_url", length = 1024)
    private String targetUrl;

    @Column(name = "cta_label", length = 60)
    private String ctaLabel;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 100;

    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
