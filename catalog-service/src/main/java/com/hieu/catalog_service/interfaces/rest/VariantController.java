package com.hieu.catalog_service.interfaces.rest;

import com.hieu.catalog_service.application.command.product.CreateProductCommand.AttrCmd;
import com.hieu.catalog_service.application.command.variant.AddVariantCommand;
import com.hieu.catalog_service.application.command.variant.AdjustVariantStockCommand;
import com.hieu.catalog_service.application.command.variant.RemoveVariantCommand;
import com.hieu.catalog_service.application.command.variant.UpdateVariantPricingCommand;
import com.hieu.catalog_service.application.command.variant.UpdateVariantStockCommand;
import com.hieu.catalog_service.application.common.CommandHandler;
import com.hieu.catalog_service.application.common.QueryHandler;
import com.hieu.catalog_service.application.dto.VariantDTO;
import com.hieu.catalog_service.application.query.variant.CheckStockQuery;
import com.hieu.catalog_service.application.query.variant.GetVariantBySkuQuery;
import com.hieu.catalog_service.interfaces.rest.dto.AddVariantRequest;
import com.hieu.catalog_service.interfaces.rest.dto.AdjustVariantStockRequest;
import com.hieu.catalog_service.interfaces.rest.dto.UpdateVariantPricingRequest;
import com.hieu.catalog_service.interfaces.rest.dto.UpdateVariantStockRequest;
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

@RestController
@RequiredArgsConstructor
@Tag(name = "Variants", description = "SKU-level operations — price, stock, attrs.")
public class VariantController {

    private static final String WRITE = "hasRole('ADMIN') or hasAuthority('catalog:write')";

    private final CommandHandler<AddVariantCommand, VariantDTO> addVariant;
    private final CommandHandler<UpdateVariantPricingCommand, VariantDTO> updatePricing;
    private final CommandHandler<UpdateVariantStockCommand, VariantDTO> updateStock;
    private final CommandHandler<AdjustVariantStockCommand, VariantDTO> adjustStock;
    private final CommandHandler<RemoveVariantCommand, Void> removeVariant;
    private final QueryHandler<GetVariantBySkuQuery, VariantDTO> getBySku;
    private final QueryHandler<CheckStockQuery, VariantDTO> checkStock;

    @Operation(summary = "Add a variant to a product (admin)")
    @PostMapping("/api/v1/products/{productId}/variants")
    @PreAuthorize(WRITE)
    public ResponseEntity<VariantDTO> add(@PathVariable Long productId,
                                            @Valid @RequestBody AddVariantRequest req,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        var attrs = req.attrs() == null ? List.<AttrCmd>of()
            : req.attrs().stream().map(a -> new AttrCmd(a.attrId(), a.attrValId(), a.valText())).toList();
        return ResponseEntity.ok(addVariant.handle(new AddVariantCommand(
            productId, req.sku(), req.price(), req.cost(), req.salePrice(),
            req.image(), req.weight(), req.quantity(), attrs, user.userId())));
    }

    @Operation(summary = "Update variant pricing (admin)")
    @PatchMapping("/api/v1/products/{productId}/variants/{variantId}/pricing")
    @PreAuthorize(WRITE)
    public ResponseEntity<VariantDTO> updatePricing(@PathVariable Long productId,
                                                      @PathVariable Long variantId,
                                                      @Valid @RequestBody UpdateVariantPricingRequest req,
                                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(updatePricing.handle(new UpdateVariantPricingCommand(
            productId, variantId, req.price(), req.cost(), req.salePrice(), user.userId())));
    }

    @Operation(summary = "Set variant stock (admin)")
    @PutMapping("/api/v1/products/{productId}/variants/{variantId}/stock")
    @PreAuthorize(WRITE)
    public ResponseEntity<VariantDTO> updateStock(@PathVariable Long productId,
                                                    @PathVariable Long variantId,
                                                    @Valid @RequestBody UpdateVariantStockRequest req,
                                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(updateStock.handle(new UpdateVariantStockCommand(
            productId, variantId, req.quantity(), user.userId())));
    }

    @Operation(summary = "Adjust variant stock by delta (admin)")
    @PostMapping("/api/v1/products/{productId}/variants/{variantId}/stock/adjust")
    @PreAuthorize(WRITE)
    public ResponseEntity<VariantDTO> adjustStock(@PathVariable Long productId,
                                                    @PathVariable Long variantId,
                                                    @Valid @RequestBody AdjustVariantStockRequest req,
                                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(adjustStock.handle(new AdjustVariantStockCommand(
            productId, variantId, req.delta(), user.userId())));
    }

    @Operation(summary = "Remove variant (admin)")
    @DeleteMapping("/api/v1/products/{productId}/variants/{variantId}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> remove(@PathVariable Long productId,
                                         @PathVariable Long variantId,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        removeVariant.handle(new RemoveVariantCommand(productId, variantId, user.userId()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get variant by SKU")
    @GetMapping("/api/v1/variants/by-sku/{sku}")
    public ResponseEntity<VariantDTO> bySku(@PathVariable String sku) {
        return ResponseEntity.ok(getBySku.handle(new GetVariantBySkuQuery(sku)));
    }

    @Operation(summary = "Check if a SKU has enough stock to fulfil a quantity")
    @GetMapping("/api/v1/variants/by-sku/{sku}/has-stock")
    public ResponseEntity<Boolean> hasStock(@PathVariable String sku,
                                              @RequestParam(defaultValue = "1") int requested) {
        VariantDTO dto = checkStock.handle(new CheckStockQuery(sku, requested));
        return ResponseEntity.ok(dto.quantity() >= requested);
    }
}
