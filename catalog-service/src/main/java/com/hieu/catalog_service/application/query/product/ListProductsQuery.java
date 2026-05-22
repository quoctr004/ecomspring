package com.hieu.catalog_service.application.query.product;

import com.hieu.catalog_service.application.common.Query;
import com.hieu.catalog_service.application.dto.PageDTO;
import com.hieu.catalog_service.application.dto.ProductSummaryDTO;

/**
 * Cursor-paginated product list query.
 *
 * <p>{@code sort} drives in-page ordering for the storefront filter bar:
 * <ul>
 *   <li>{@code newest}   — created_at DESC (default; native cursor key)</li>
 *   <li>{@code priceAsc} / {@code priceDesc} — min_price + id tie-break</li>
 *   <li>{@code nameAsc}  / {@code nameDesc}  — name + id tie-break</li>
 * </ul>
 *
 * <p>{@code categoryId} narrows the result to a single category when set.
 *
 * <p>Non-default sorts fall back to offset pagination (cursor ignored) because
 * a keyset cursor must encode the active sort key — wiring multi-key cursors
 * isn't worth it for the storefront's "switch sort" flow which always resets
 * the page.
 */
public record ListProductsQuery(
        String cursor,
        int limit,
        String sort,
        Long categoryId
) implements Query<PageDTO<ProductSummaryDTO>> {
    public ListProductsQuery(String cursor, int limit) {
        this(cursor, limit, null, null);
    }
}
