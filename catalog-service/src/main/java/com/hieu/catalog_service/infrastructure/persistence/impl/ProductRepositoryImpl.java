package com.hieu.catalog_service.infrastructure.persistence.impl;

import com.hieu.catalog_service.domain.model.product.Product;
import com.hieu.catalog_service.domain.model.product.valueobject.ProductId;
import com.hieu.catalog_service.domain.model.product.valueobject.Sku;
import com.hieu.catalog_service.domain.model.product.valueobject.Slug;
import com.hieu.catalog_service.domain.repository.ProductRepository;
import com.hieu.catalog_service.infrastructure.persistence.jpa.entities.ProductJpaEntity;
import com.hieu.catalog_service.infrastructure.persistence.jpa.repositories.ProductJpaRepository;
import com.hieu.catalog_service.infrastructure.persistence.mapper.ProductJpaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JPA adapter for {@link ProductRepository}. Operates on the SAME domain aggregate that
 * was passed in (no clone-and-return) so the caller's {@code publishEventsOf} sees the
 * registered events intact.
 */
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository jpa;
    private final ProductJpaMapper mapper;

    @Override
    public Product save(Product product) {
        ProductJpaEntity existing = product.getId() != null
            ? jpa.findByIdWithVariants(product.getId().value()).orElse(null)
            : null;
        ProductJpaEntity saved = jpa.saveAndFlush(mapper.toJpa(product, existing));
        mapper.syncGeneratedIds(product, saved);
        return product;
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        return jpa.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Product> findByIdWithVariants(ProductId id) {
        return jpa.findByIdWithVariants(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Product> findBySlug(Slug slug) {
        // Hydrate variants in the same query — callers (detail page handler) need
        // them and previously had to fire a second findByIdWithVariants round-trip.
        return jpa.findBySlugWithVariants(slug.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Product> findBySku(Sku sku) {
        return jpa.findBySkuWithVariants(sku.value())
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsBySlug(Slug slug) {
        return jpa.existsBySlug(slug.value());
    }

    @Override
    public boolean existsBySku(Sku sku) {
        return jpa.existsBySku(sku.value());
    }

    @Override
    public List<ProductId> findFirstPageIds(int limit) {
        return jpa.findFirstPageIds(PageRequest.of(0, limit))
            .stream().map(ProductId::of).toList();
    }

    @Override
    public List<ProductId> findIdsAfterCursor(Instant createdAt, Long id, int limit) {
        return jpa.findIdsAfterCursor(createdAt, id, PageRequest.of(0, limit))
            .stream().map(ProductId::of).toList();
    }

    @Override
    public List<Product> findAllByIdsWithVariants(List<ProductId> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        List<Long> raw = ids.stream().map(ProductId::value).toList();
        List<ProductJpaEntity> rows = jpa.findAllByIdInWithVariants(raw);
        // Preserve the caller's requested ordering (JPA IN clause loses it).
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < raw.size(); i++) order.put(raw.get(i), i);
        return rows.stream()
            .sorted(Comparator.comparingInt(p -> order.getOrDefault(p.getId(), Integer.MAX_VALUE)))
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<ProductId> findIdsSorted(String sort, Long categoryId, int offset, int limit) {
        PageRequest page = PageRequest.of(offset / Math.max(1, limit), limit);
        String key = sort == null ? "newest" : sort.toLowerCase();
        List<Long> raw = switch (key) {
            case "priceasc"  -> jpa.findIdsSortedPriceAsc(categoryId, page);
            case "pricedesc" -> jpa.findIdsSortedPriceDesc(categoryId, page);
            case "nameasc"   -> jpa.findIdsSortedNameAsc(categoryId, page);
            case "namedesc"  -> jpa.findIdsSortedNameDesc(categoryId, page);
            default          -> jpa.findIdsSortedNewest(categoryId, page);
        };
        return raw.stream().map(ProductId::of).toList();
    }

    @Override
    public void delete(Product product) {
        if (product.getId() != null) jpa.deleteById(product.getId().value());
    }
}
