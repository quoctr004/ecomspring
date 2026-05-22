package com.hieu.catalog_service.application.handler.product;

import com.hieu.catalog_service.application.common.CursorCodec;
import com.hieu.catalog_service.application.common.QueryHandler;
import com.hieu.catalog_service.application.dto.PageDTO;
import com.hieu.catalog_service.application.dto.ProductSummaryDTO;
import com.hieu.catalog_service.application.mapper.CatalogDtoMapper;
import com.hieu.catalog_service.application.query.product.ListProductsQuery;
import com.hieu.catalog_service.domain.model.product.Product;
import com.hieu.catalog_service.domain.model.product.valueobject.ProductId;
import com.hieu.catalog_service.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Cursor-paginated product listing.
 *
 * <p>The default "newest" sort uses keyset pagination on {@code (createdAt, id)}
 * — O(log n) per page regardless of depth. Non-default sorts (price, name)
 * degrade to offset pagination since a single cursor key can't encode the
 * sort column too. Storefronts always reset to page 1 when toggling sort
 * filters, so this is fine in practice.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListProductsHandler implements QueryHandler<ListProductsQuery, PageDTO<ProductSummaryDTO>> {

    private static final int MAX_LIMIT = 100;
    private static final Set<String> KEYSET_SORTS = Set.of("newest", "");

    private final ProductRepository productRepository;
    private final CatalogDtoMapper mapper;

    @Override
    public PageDTO<ProductSummaryDTO> handle(ListProductsQuery query) {
        int pageSize = Math.clamp(query.limit(), 1, MAX_LIMIT);
        String sort = query.sort() == null ? "newest" : query.sort();
        Long categoryId = query.categoryId();

        boolean useKeyset = KEYSET_SORTS.contains(sort.toLowerCase()) && categoryId == null;
        return useKeyset ? handleKeyset(query, pageSize) : handleSorted(sort, categoryId, query.cursor(), pageSize);
    }

    private PageDTO<ProductSummaryDTO> handleKeyset(ListProductsQuery query, int pageSize) {
        var cursor = CursorCodec.decode(query.cursor());

        List<ProductId> ids = cursor == null
            ? productRepository.findFirstPageIds(pageSize + 1)
            : productRepository.findIdsAfterCursor(cursor.createdAt(), cursor.id(), pageSize + 1);

        boolean hasNext = ids.size() > pageSize;
        List<ProductId> pageIds = hasNext ? ids.subList(0, pageSize) : ids;

        List<Product> products = productRepository.findAllByIdsWithVariants(pageIds);
        List<ProductSummaryDTO> items = products.stream().map(mapper::toSummary).toList();

        String next = null;
        if (hasNext && !products.isEmpty()) {
            Product last = products.getLast();
            next = CursorCodec.encode(last.getCreatedAt(), last.getId().value());
        }
        return PageDTO.of(items, next, pageSize, -1);
    }

    /**
     * Offset-based fallback for price / name sorts. Cursor here is "page=N"
     * (just a number) — the FE never inspects it.
     */
    private PageDTO<ProductSummaryDTO> handleSorted(String sort, Long categoryId, String cursor, int pageSize) {
        int page = 0;
        try { if (cursor != null && !cursor.isBlank()) page = Math.max(0, Integer.parseInt(cursor)); }
        catch (NumberFormatException ignored) {}

        int offset = page * pageSize;
        List<ProductId> ids = productRepository.findIdsSorted(sort, categoryId, offset, pageSize + 1);
        boolean hasNext = ids.size() > pageSize;
        List<ProductId> pageIds = hasNext ? ids.subList(0, pageSize) : ids;

        List<Product> products = productRepository.findAllByIdsWithVariants(pageIds);
        List<ProductSummaryDTO> items = products.stream().map(mapper::toSummary).toList();

        String next = hasNext ? String.valueOf(page + 1) : null;
        return PageDTO.of(items, next, pageSize, -1);
    }
}
