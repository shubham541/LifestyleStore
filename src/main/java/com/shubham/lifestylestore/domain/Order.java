package com.shubham.lifestylestore.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Line items snapshot name and price at purchase time so later catalog edits never rewrite history.
 *
 * <p>{@code subtotalPaise} and {@code shippingPaise} are boxed because orders stored before shipping was introduced
 * do not have them; a primitive would make Spring Data refuse to load those documents. Use
 * {@link #withDerivedTotals()} before returning an order to a client.
 */
@Document("orders")
public record Order(
        @Id String id,
        @Indexed String userId,
        List<OrderItem> items,
        Long subtotalPaise,
        Long shippingPaise,
        long totalPaise,
        String status,
        String shippingAddress,
        Instant createdAt) {

    public record OrderItem(String productId, String name, long unitPricePaise, int quantity) {
    }

    /** Fills in the subtotal and shipping of legacy orders (shipping was free back then, so total == subtotal). */
    public Order withDerivedTotals() {
        if (subtotalPaise != null && shippingPaise != null) {
            return this;
        }
        long subtotal = subtotalPaise != null ? subtotalPaise
                : items == null ? totalPaise
                : items.stream().mapToLong(i -> i.unitPricePaise() * i.quantity()).sum();
        long shipping = shippingPaise != null ? shippingPaise : Math.max(0, totalPaise - subtotal);
        return new Order(id, userId, items, subtotal, shipping, totalPaise, status, shippingAddress, createdAt);
    }
}
