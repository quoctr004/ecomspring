package com.hieu.catalog_service.domain.repository;

import com.hieu.catalog_service.domain.model.product.Product;
import com.hieu.catalog_service.domain.model.product.valueobject.ProductId;
import com.hieu.catalog_service.domain.model.product.valueobject.Sku;
import com.hieu.catalog_service.domain.model.product.valueobject.Slug;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository port for the {@link Product} aggregate. Implemented by the JPA adapter.
 *
 * <p>Read methods are expected to use JOIN FETCH / entity graphs so a single call returns
 * a fully-hydrated aggregate — the application layer never peeks behind the port and must
 * not trigger lazy-loading on detached domain objects.
 *
 * <p>The cursor-pagination pair ({@link #findFirstPageIds(int)} / {@link #findIdsAfterCursor})
 * mirrors the auth-service convention: the application composes an opaque cursor from
 * {@code (createdAt, id)}; here we expose the decoded tuple so the adapter can run the
 * correct index-friendly query.
 */
public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(ProductId id);

    /** Full hydration (with variants + attrs) — used by GetProductByIdHandler. */
    Optional<Product> findByIdWithVariants(ProductId id);

    Optional<Product> findBySlug(Slug slug);

    /** Returns the product owning the given SKU, if any. */
    Optional<Product> findBySku(Sku sku);

    boolean existsBySlug(Slug slug);

    boolean existsBySku(Sku sku);

    /** Cursor pagination — first page: top N by {@code createdAt DESC, id DESC}. */
    List<ProductId> findFirstPageIds(int limit);

    /** Cursor pagination — rows strictly older than {@code (createdAt, id)}. */
    List<ProductId> findIdsAfterCursor(Instant createdAt, Long id, int limit);

    /** Batch hydration preserving the input order — used after cursor lookup. */
    List<Product> findAllByIdsWithVariants(List<ProductId> ids);

    /**
     * Sorted, optionally category-filtered offset page. Used when the storefront
     * picks a non-default sort (price, name) — cursor pagination only works on
     * the createdAt-DESC key.
     *
     * @param sort       one of {@code priceAsc|priceDesc|nameAsc|nameDesc|newest}
     * @param categoryId narrow to this category or null for all
     * @param offset     0-based row offset
     * @param limit      page size
     */
    List<ProductId> findIdsSorted(String sort, Long categoryId, int offset, int limit);

    void delete(Product product);
}
