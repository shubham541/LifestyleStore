package com.shubham.lifestylestore.domain;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Prices are stored in paise (minor units) to avoid floating-point money bugs. */
@Document("products")
public record Product(
        @Id String id,
        String name,
        String brand,
        String description,
        @Indexed Category category,
        long pricePaise,
        long mrpPaise,
        int discountPercent,
        double rating,
        int ratingCount,
        List<String> tags,
        int stock,
        String imageUrl,
        String imageNote) {

    public Product withStock(int newStock) {
        return new Product(id, name, brand, description, category, pricePaise, mrpPaise, discountPercent, rating,
                ratingCount, tags, newStock, imageUrl, imageNote);
    }
}
