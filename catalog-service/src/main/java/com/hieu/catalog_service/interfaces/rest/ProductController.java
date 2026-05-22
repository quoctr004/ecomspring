package com.hieu.catalog_service.interfaces.rest;

import com.hieu.catalog_service.application.command.product.ChangeProductStatusCommand;
import com.hieu.catalog_service.application.command.product.ChangeProductStatusCommand.Transition;
import com.hieu.catalog_service.application.command.product.CreateProductCommand;
import com.hieu.catalog_service.application.command.product.DeleteProductCommand;
import com.hieu.catalog_service.application.command.product.UpdateProductCommand;
import com.hieu.catalog_service.application.command.product.UpdateProductImagesCommand;
import com.hieu.catalog_service.application.command.product.UpdateProductSeoCommand;
import com.hieu.catalog_service.application.common.CommandHandler;
import com.hieu.catalog_service.application.common.QueryHandler;
import com.hieu.catalog_service.application.dto.PageDTO;
import com.hieu.catalog_service.application.dto.ProductDTO;
import com.hieu.catalog_service.application.dto.ProductSummaryDTO;
import com.hieu.catalog_service.application.query.product.GetProductByIdQuery;
import com.hieu.catalog_service.application.query.product.GetProductBySlugQuery;
import com.hieu.catalog_service.application.query.product.ListProductsQuery;
import com.hieu.catalog_service.interfaces.rest.dto.CreateProductRequest;
import com.hieu.catalog_service.interfaces.rest.dto.UpdateImagesRequest;
import com.hieu.catalog_service.interfaces.rest.dto.UpdateProductRequest;
import com.hieu.catalog_service.interfaces.rest.dto.UpdateSeoRequest;
import com.hieu.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Product CRUD + lifecycle. Reads are public; mutations require either
 * {@code ROLE_ADMIN} or the {@code catalog:write} permission (for future
 * catalog-manager roles that aren't full admins).
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog — browsing + admin CRUD.")
public class ProductController {

    private static final String WRITE = "hasRole('ADMIN') or hasAuthority('catalog:write')";

    private final CommandHandler<CreateProductCommand, ProductDTO> createProduct;
    private final CommandHandler<UpdateProductCommand, ProductDTO> updateProduct;
    private final CommandHandler<UpdateProductImagesCommand, ProductDTO> updateImages;
    private final CommandHandler<UpdateProductSeoCommand, ProductDTO> updateSeo;
    private final CommandHandler<ChangeProductStatusCommand, Void> changeStatus;
    private final CommandHandler<DeleteProductCommand, Void> deleteProduct;

    private final QueryHandler<GetProductByIdQuery, ProductDTO> getById;
    private final QueryHandler<GetProductBySlugQuery, ProductDTO> getBySlug;
    private final QueryHandler<ListProductsQuery, PageDTO<ProductSummaryDTO>> listProducts;

    @Operation(summary = "List products (cursor pagination; sort=newest|priceAsc|priceDesc|nameAsc|nameDesc)")
    @GetMapping
    public ResponseEntity<PageDTO<ProductSummaryDTO>> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(listProducts.handle(
                new ListProductsQuery(cursor, limit, sort, categoryId)));
    }

    @Operation(summary = "Get product by id")
    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> byId(@PathVariable Long id) {
        return ResponseEntity.ok(getById.handle(new GetProductByIdQuery(id)));
    }

    @Operation(summary = "Get product by slug")
    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<ProductDTO> bySlug(@PathVariable String slug) {
        return ResponseEntity.ok(getBySlug.handle(new GetProductBySlugQuery(slug)));
    }

    @Operation(summary = "Create product (admin)")
    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<ProductDTO> create(@Valid @RequestBody CreateProductRequest req,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        var variants = req.variants().stream()
            .map(v -> new CreateProductCommand.VariantCmd(
                v.sku(), v.price(), v.cost(), v.salePrice(), v.image(), v.weight(), v.quantity(),
                v.attrs() == null ? List.of() : v.attrs().stream()
                    .map(a -> new CreateProductCommand.AttrCmd(a.attrId(), a.attrValId(), a.valText()))
                    .toList()))
            .toList();
        var cmd = new CreateProductCommand(req.name(), req.description(), req.categoryId(), req.brand(),
            req.thumbnail(), req.images(), req.metaTitle(), req.metaDescription(), req.metaKeywords(),
            variants, req.activateOrFalse(), user.userId());
        return ResponseEntity.ok(createProduct.handle(cmd));
    }

    @Operation(summary = "Update product core fields (admin)")
    @PatchMapping("/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<ProductDTO> update(@PathVariable Long id,
                                              @Valid @RequestBody UpdateProductRequest req,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(updateProduct.handle(
            new UpdateProductCommand(id, req.name(), req.description(), req.categoryId(), req.brand(), user.userId())));
    }

    @Operation(summary = "Update product images (admin)")
    @PatchMapping("/{id}/images")
    @PreAuthorize(WRITE)
    public ResponseEntity<ProductDTO> updateImages(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateImagesRequest req,
                                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(updateImages.handle(
            new UpdateProductImagesCommand(id, req.thumbnail(), req.images(), user.userId())));
    }

    @Operation(summary = "Update product SEO metadata (admin)")
    @PatchMapping("/{id}/seo")
    @PreAuthorize(WRITE)
    public ResponseEntity<ProductDTO> updateSeo(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateSeoRequest req,
                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(updateSeo.handle(
            new UpdateProductSeoCommand(id, req.metaTitle(), req.metaDescription(), req.metaKeywords(), user.userId())));
    }

    @Operation(summary = "Change product status (activate / deactivate / draft) (admin)")
    @PostMapping("/{id}/status/{transition}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> changeStatus(@PathVariable Long id,
                                              @PathVariable Transition transition,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        changeStatus.handle(new ChangeProductStatusCommand(id, transition, user.userId()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Soft-delete product (admin)")
    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        deleteProduct.handle(new DeleteProductCommand(id, user.userId()));
        return ResponseEntity.noContent().build();
    }
}
