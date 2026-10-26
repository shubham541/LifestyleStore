package com.shubham.lifestylestore.config;

import com.shubham.lifestylestore.domain.Category;
import com.shubham.lifestylestore.domain.Product;
import com.shubham.lifestylestore.repo.ProductRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the catalogue in sync with {@code seed/products.json}. The seed file is the source of truth for product
 * data (name, price, rating...), but live stock levels of products that already exist are preserved, and products
 * that are no longer in the seed are removed. Existing documents are read raw so that databases created by an
 * older version of the schema migrate cleanly instead of failing to map.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String COLLECTION = "products";

    record SeedProduct(String id, String name, String brand, Category category, long price, long mrp, int stock,
                       double rating, int ratings, List<String> tags, String description, String image,
                       String imageNote) {
    }

    private final ProductRepository products;
    private final MongoTemplate mongo;
    private final JsonMapper mapper;

    public DataSeeder(ProductRepository products, MongoTemplate mongo, JsonMapper mapper) {
        this.products = products;
        this.mongo = mongo;
        this.mapper = mapper;
    }

    @Override
    public void run(String... args) throws IOException {
        List<SeedProduct> seed;
        try (InputStream in = new ClassPathResource("seed/products.json").getInputStream()) {
            seed = List.of(mapper.readValue(in, SeedProduct[].class));
        }

        // id -> current stock, only for documents already in the current schema (they carry mrpPaise)
        Map<String, Integer> currentStock = new HashMap<>();
        Set<String> existingIds = new HashSet<>();
        for (Document d : mongo.getCollection(COLLECTION).find()) {
            String id = String.valueOf(d.get("_id"));
            existingIds.add(id);
            if (d.containsKey("mrpPaise") && d.get("stock") instanceof Number n) {
                currentStock.put(id, n.intValue());
            }
        }

        Set<String> seedIds = new HashSet<>();
        int created = 0;
        for (SeedProduct s : seed) {
            seedIds.add(s.id());
            Integer stock = currentStock.get(s.id());
            if (stock == null) {
                created++;
            }
            products.save(toProduct(s, stock == null ? s.stock() : stock));
        }

        List<String> stale = existingIds.stream().filter(id -> !seedIds.contains(id)).toList();
        if (!stale.isEmpty()) {
            mongo.remove(Query.query(Criteria.where("_id").in(stale)), COLLECTION);
        }
        log.info("Catalogue synced: {} products ({} new, {} removed)", seed.size(), created, stale.size());
    }

    private static Product toProduct(SeedProduct s, int stock) {
        long pricePaise = s.price() * 100;
        long mrpPaise = s.mrp() * 100;
        int discount = mrpPaise > pricePaise ? (int) Math.round(100.0 * (mrpPaise - pricePaise) / mrpPaise) : 0;
        String image = s.image() == null ? null : "/img/" + s.image();
        return new Product(s.id(), s.name(), s.brand(), s.description(), s.category(), pricePaise, mrpPaise, discount,
                s.rating(), s.ratings(), s.tags(), stock, image, s.imageNote());
    }
}
