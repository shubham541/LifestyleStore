package com.shubham.lifestylestore.service;

import com.shubham.lifestylestore.domain.Cart;
import com.shubham.lifestylestore.domain.Cart.CartItem;
import com.shubham.lifestylestore.domain.Product;
import com.shubham.lifestylestore.repo.CartRepository;
import com.shubham.lifestylestore.repo.ProductRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CartService {

    public static final int MAX_QUANTITY = 10;
    public static final int MAX_DISTINCT_ITEMS = 20;

    private final CartRepository carts;
    private final ProductRepository products;

    public CartService(CartRepository carts, ProductRepository products) {
        this.carts = carts;
        this.products = products;
    }

    public record Line(String productId, String name, String brand, String category, String imageUrl,
                       long unitPricePaise, long mrpPaise, int quantity, int stock, long linePaise) {
    }

    public record CartView(List<Line> items, int itemCount, long subtotalPaise, long savingsPaise, long shippingPaise,
                           long totalPaise, long freeShippingAbovePaise) {
    }

    /** Every operation is keyed by the authenticated user id, so one user can never touch another's cart. */
    public CartView view(String userId) {
        return toView(load(userId));
    }

    public CartView setQuantity(String userId, String productId, int quantity) {
        if (quantity < 0 || quantity > MAX_QUANTITY) {
            throw ApiException.badRequest("Quantity must be between 0 and " + MAX_QUANTITY);
        }
        products.findById(productId).orElseThrow(() -> ApiException.notFound("Product"));
        Cart cart = load(userId);
        List<CartItem> items = new ArrayList<>(cart.items());
        items.removeIf(i -> i.productId().equals(productId));
        if (quantity > 0) {
            if (items.size() >= MAX_DISTINCT_ITEMS) {
                throw ApiException.badRequest("Cart is full");
            }
            items.add(new CartItem(productId, quantity));
        }
        return toView(carts.save(new Cart(cart.id(), userId, items, Instant.now())));
    }

    public CartView add(String userId, String productId, int quantity) {
        int existing = load(userId).items().stream().filter(i -> i.productId().equals(productId))
                .mapToInt(CartItem::quantity).sum();
        return setQuantity(userId, productId, Math.min(existing + quantity, MAX_QUANTITY));
    }

    public void clear(String userId) {
        Cart cart = load(userId);
        carts.save(new Cart(cart.id(), userId, List.of(), Instant.now()));
    }

    public Cart load(String userId) {
        return carts.findByUserId(userId)
                .orElseGet(() -> new Cart(UUID.randomUUID().toString(), userId, List.of(), Instant.now()));
    }

    private CartView toView(Cart cart) {
        Map<String, Product> byId = products.findAllById(cart.items().stream().map(CartItem::productId).toList())
                .stream().collect(Collectors.toMap(Product::id, Function.identity()));
        List<Line> lines = new ArrayList<>();
        long subtotal = 0;
        long savings = 0;
        int count = 0;
        for (CartItem item : cart.items()) {
            Product p = byId.get(item.productId());
            if (p == null) {
                continue; // product removed from catalogue
            }
            long line = p.pricePaise() * item.quantity();
            subtotal += line;
            savings += Math.max(0, p.mrpPaise() - p.pricePaise()) * item.quantity();
            count += item.quantity();
            lines.add(new Line(p.id(), p.name(), p.brand(), p.category().name(), p.imageUrl(), p.pricePaise(),
                    p.mrpPaise(), item.quantity(), p.stock(), line));
        }
        long shipping = Pricing.shippingFor(subtotal);
        return new CartView(lines, count, subtotal, savings, shipping, subtotal + shipping,
                Pricing.FREE_SHIPPING_ABOVE_PAISE);
    }
}
