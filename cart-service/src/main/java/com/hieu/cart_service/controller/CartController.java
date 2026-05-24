package com.hieu.cart_service.controller;

import com.hieu.cart_service.dto.AddToCartRequest;
import com.hieu.cart_service.dto.CartDTO;
import com.hieu.cart_service.dto.UpdateCartItemRequest;
import com.hieu.cart_service.service.CartService;
import com.hieu.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** REST controller for cart operations. All endpoints require JWT. */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Manage user shopping cart")
@SecurityRequirement(name = "bearerAuth")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get current user's cart")
    public ResponseEntity<CartDTO> getCart(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(cartService.getCart(user.userId()));
    }

    @PostMapping("/items")
    @Operation(summary = "Add or upsert an item into the cart")
    public ResponseEntity<CartDTO> addItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddToCartRequest req) {
        return ResponseEntity.ok(cartService.addItem(user.userId(), req));
    }

    @PutMapping("/items/{variantId}")
    @Operation(summary = "Update item quantity (0 = delete)")
    public ResponseEntity<CartDTO> updateItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateCartItemRequest req) {
        return ResponseEntity.ok(cartService.updateItem(user.userId(), variantId, req));
    }

    @DeleteMapping("/items/{variantId}")
    @Operation(summary = "Remove a single item from cart")
    public ResponseEntity<Void> removeItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long variantId) {
        cartService.removeItem(user.userId(), variantId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Clear entire cart")
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal AuthenticatedUser user) {
        cartService.clearCart(user.userId());
        return ResponseEntity.noContent().build();
    }
}
