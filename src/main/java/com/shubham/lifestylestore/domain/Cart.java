package com.shubham.lifestylestore.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("carts")
public record Cart(@Id String id, @Indexed(unique = true) String userId, List<CartItem> items, Instant updatedAt) {

    public record CartItem(String productId, int quantity) {
    }
}
