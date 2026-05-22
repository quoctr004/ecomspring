package com.hieu.catalog_service.infrastructure.persistence.jpa.repositories;

import com.hieu.catalog_service.infrastructure.persistence.jpa.entities.ProductJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {

    /**
     * Full hydration with variants + variant attrs in a single query. The DISTINCT avoids
     * duplicate Product rows introduced by the two LEFT JOINs.
     */
    @Query("""
           SELECT DISTINCT p FROM ProductJpaEntity p
           LEFT JOIN FETCH p.variants v
           WHERE p.id = :id
           """)
    Optional<ProductJpaEntity> findByIdWithVariants(@Param("id") Long id);

    @Query("SELECT p FROM ProductJpaEntity p WHERE p.slug = :slug")
    Optional<ProductJpaEntity> findBySlug(@Param("slug") String slug);

    /** Single-query variant: fetch product + all its variants when looking up by slug. */
    @Query("""
           SELECT DISTINCT p FROM ProductJpaEntity p
           LEFT JOIN FETCH p.variants v
           WHERE p.slug = :slug
           """)
    Optional<ProductJpaEntity> findBySlugWithVariants(@Param("slug") String slug);

    @Query("""
           SELECT DISTINCT p FROM ProductJpaEntity p
           JOIN p.variants v
           WHERE v.sku = :sku
           """)
    Optional<ProductJpaEntity> findBySku(@Param("sku") String sku);

    /** Single-query variant: fetch product + all its variants when looking up by SKU. */
    @Query("""
           SELECT DISTINCT p FROM ProductJpaEntity p
           LEFT JOIN FETCH p.variants v
           WHERE v.sku = :sku
           """)
    Optional<ProductJpaEntity> findBySkuWithVariants(@Param("sku") String sku);

    boolean existsBySlug(String slug);

    @Query("SELECT COUNT(v) > 0 FROM VariantJpaEntity v WHERE v.sku = :sku")
    boolean existsBySku(@Param("sku") String sku);

    // ── Cursor pagination ─────────────────────────────────────────────────────
    // Postgres refuses to infer types for null parameters in predicates, so the
    // first-page query is split from the cursor-advance query.

    @Query("""
           SELECT p.id FROM ProductJpaEntity p
           WHERE p.status <> 'DELETED'
           ORDER BY p.createdAt DESC, p.id DESC
           """)
    List<Long> findFirstPageIds(Pageable pageable);

    @Query("""
           SELECT p.id FROM ProductJpaEntity p
           WHERE p.status <> 'DELETED'
             AND (p.createdAt < :cursorCreatedAt
                  OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId))
           ORDER BY p.createdAt DESC, p.id DESC
           """)
    List<Long> findIdsAfterCursor(@Param("cursorCreatedAt") Instant cursorCreatedAt,
                                   @Param("cursorId") Long cursorId,
                                   Pageable pageable);

    /** Batch hydrate — order by {@code createdAt DESC, id DESC} to match the cursor query. */
    @Query("""
           SELECT DISTINCT p FROM ProductJpaEntity p
           LEFT JOIN FETCH p.variants v
           WHERE p.id IN :ids
           ORDER BY p.createdAt DESC, p.id DESC
           """)
    List<ProductJpaEntity> findAllByIdInWithVariants(@Param("ids") List<Long> ids);

    // ── Sorted offset pagination (price / name) ───────────────────────────────
    // Sort over MIN(variant.price) per product. Category filter is optional;
    // we duplicate the four ORDER BY directions because JPQL doesn't support
    // dynamic sort columns and Spring's Pageable+Sort on aggregate columns is
    // brittle. Two queries per sort × category-or-not (= 4 each) is verbose
    // but predictable.

    @Query("""
           SELECT p.id FROM ProductJpaEntity p
           LEFT JOIN p.variants v
           WHERE p.status <> 'DELETED'
             AND (:categoryId IS NULL OR p.categoryId = :categoryId)
           GROUP BY p.id, p.name, p.createdAt
           ORDER BY MIN(COALESCE(v.salePrice, v.price)) ASC, p.id ASC
           """)
    List<Long> findIdsSortedPriceAsc(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query("""
           SELECT p.id FROM ProductJpaEntity p
           LEFT JOIN p.variants v
           WHERE p.status <> 'DELETED'
             AND (:categoryId IS NULL OR p.categoryId = :categoryId)
           GROUP BY p.id, p.name, p.createdAt
           ORDER BY MIN(COALESCE(v.salePrice, v.price)) DESC, p.id DESC
           """)
    List<Long> findIdsSortedPriceDesc(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query("""
           SELECT p.id FROM ProductJpaEntity p
           WHERE p.status <> 'DELETED'
             AND (:categoryId IS NULL OR p.categoryId = :categoryId)
           ORDER BY p.name ASC, p.id ASC
           """)
    List<Long> findIdsSortedNameAsc(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query("""
           SELECT p.id FROM ProductJpaEntity p
           WHERE p.status <> 'DELETED'
             AND (:categoryId IS NULL OR p.categoryId = :categoryId)
           ORDER BY p.name DESC, p.id DESC
           """)
    List<Long> findIdsSortedNameDesc(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query("""
           SELECT p.id FROM ProductJpaEntity p
           WHERE p.status <> 'DELETED'
             AND (:categoryId IS NULL OR p.categoryId = :categoryId)
           ORDER BY p.createdAt DESC, p.id DESC
           """)
    List<Long> findIdsSortedNewest(@Param("categoryId") Long categoryId, Pageable pageable);
}
