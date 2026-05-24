package com.hieu.search_service.controller;

import com.hieu.common.api.ApiResponse;
import com.hieu.search_service.document.ProductDocument;
import com.hieu.search_service.dto.IndexProductRequest;
import com.hieu.search_service.dto.PageResponse;
import com.hieu.search_service.dto.SearchRequest;
import com.hieu.search_service.service.SearchApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Validated  // enables @Size on method parameters
@Tag(name = "Search", description = "Product search & indexing endpoints")
public class SearchController {

    private final SearchApplicationService searchService;

    /** POST /api/search — full-text search via request body */
    @PostMapping
    @Operation(summary = "Search products (body)")
    public ResponseEntity<ApiResponse<PageResponse<ProductDocument>>> searchByBody(
            @RequestBody SearchRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(searchService.searchProducts(req)));
    }

    /** GET /api/search — search via query params */
    @GetMapping
    @Operation(summary = "Search products (query params)")
    public ResponseEntity<ApiResponse<PageResponse<ProductDocument>>> searchByParams(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var req = SearchRequest.builder()
                .q(q).brand(brand).categoryId(categoryId)
                .minPrice(minPrice).maxPrice(maxPrice).status(status)
                .sortBy(sortBy).page(page).size(size)
                .build();
        return ResponseEntity.ok(ApiResponse.ok(searchService.searchProducts(req)));
    }

    /** GET /api/search/suggest — public autocomplete */
    @GetMapping("/suggest")
    @Operation(summary = "Get search suggestions (prefix match on name)")
    public ResponseEntity<ApiResponse<List<String>>> suggest(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(searchService.getSuggestions(q, size)));
    }

    /** POST /api/search/index — index a single product (admin only) */
    @PostMapping("/index")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Index a product document (admin)")
    public ResponseEntity<ApiResponse<Void>> indexProduct(
            @Valid @RequestBody IndexProductRequest req) {
        searchService.indexProduct(req);
        return ResponseEntity.ok(ApiResponse.ok(null, "Product indexed"));
    }

    /** DELETE /api/search/index/{productId} — remove from index (admin only) */
    @DeleteMapping("/index/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remove a product from the index (admin)")
    public ResponseEntity<ApiResponse<Void>> removeProduct(@PathVariable String productId) {
        searchService.removeProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Product removal scheduled"));
    }

    /** POST /api/search/index/reindex — capped bulk reindex (admin only) */
    @PostMapping("/index/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bulk reindex products, max 1000 per batch (admin)")
    public ResponseEntity<Map<String, Object>> reindexAll(
            // Cap batch size to prevent OOM / unbounded ES bulk requests
            @RequestBody @Size(max = 1000, message = "Max 1000 products per batch") @Valid List<IndexProductRequest> requests) {
        int n = searchService.reindexAll(requests);
        return ResponseEntity.ok(Map.of("indexed", n));
    }
}
