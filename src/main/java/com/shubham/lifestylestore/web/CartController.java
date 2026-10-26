package com.shubham.lifestylestore.web;

import com.shubham.lifestylestore.service.CartService;
import com.shubham.lifestylestore.service.CartService.CartView;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cart")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/cart")
public class CartController {

    public record AddItemRequest(@NotBlank @Size(max = 40) String productId,
                                 @Min(1) @Max(CartService.MAX_QUANTITY) int quantity) {
    }

    public record QuantityRequest(@Min(0) @Max(CartService.MAX_QUANTITY) int quantity) {
    }

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    public CartView view(@AuthenticationPrincipal Jwt jwt) {
        return cart.view(jwt.getSubject());
    }

    @PostMapping("/items")
    public CartView add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddItemRequest r) {
        return cart.add(jwt.getSubject(), r.productId(), r.quantity());
    }

    @PutMapping("/items/{productId}")
    public CartView setQuantity(@AuthenticationPrincipal Jwt jwt, @PathVariable String productId,
                                @Valid @RequestBody QuantityRequest r) {
        return cart.setQuantity(jwt.getSubject(), productId, r.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartView remove(@AuthenticationPrincipal Jwt jwt, @PathVariable String productId) {
        return cart.setQuantity(jwt.getSubject(), productId, 0);
    }
}
