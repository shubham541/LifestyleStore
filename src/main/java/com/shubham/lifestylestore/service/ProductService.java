package com.shubham.lifestylestore.service;

import com.shubham.lifestylestore.domain.Category;
import com.shubham.lifestylestore.domain.Product;
import com.shubham.lifestylestore.domain.ProductQuery;
import com.shubham.lifestylestore.repo.ProductRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SEARCH_TOKENS = 5;

    private final ProductRepository repository;
    private final MongoTemplate mongo;

    public ProductService(ProductRepository repository, MongoTemplate mongo) {
        this.repository = repository;
        this.mongo = mongo;
    }

    public record CategoryInfo(String key, String label, String emoji, String group, long count, List<String> brands) {
    }

    public record Meta(List<CategoryInfo> categories, long minPricePaise, long maxPricePaise) {
    }

    public Page<Product> search(ProductQuery q, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sortFor(q));
        Query query = buildQuery(q);
        List<Product> content = mongo.find(Query.of(query).with(pageable), Product.class);
        return PageableExecutionUtils.getPage(content, pageable,
                () -> mongo.count(Query.of(query).limit(-1).skip(-1), Product.class));
    }

    private static Sort sortFor(ProductQuery q) {
        return switch (q.sort() == null ? ProductQuery.SortOrder.RELEVANCE : q.sort()) {
            case PRICE_ASC -> Sort.by(Sort.Order.asc("pricePaise"), Sort.Order.desc("ratingCount"));
            case PRICE_DESC -> Sort.by(Sort.Order.desc("pricePaise"), Sort.Order.desc("ratingCount"));
            case RATING -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("ratingCount"));
            case DISCOUNT -> Sort.by(Sort.Order.desc("discountPercent"), Sort.Order.desc("ratingCount"));
            case RELEVANCE -> Sort.by(Sort.Order.desc("ratingCount"), Sort.Order.asc("id"));
        };
    }

    private static Query buildQuery(ProductQuery q) {
        List<Criteria> all = new ArrayList<>();
        if (q.category() != null) {
            all.add(Criteria.where("category").is(q.category()));
        }
        if (q.brand() != null && !q.brand().isBlank()) {
            all.add(Criteria.where("brand").regex("^" + Pattern.quote(q.brand().trim()) + "$", "i"));
        }
        if (q.minPaise() != null) {
            all.add(Criteria.where("pricePaise").gte(q.minPaise()));
        }
        if (q.maxPaise() != null) {
            all.add(Criteria.where("pricePaise").lte(q.maxPaise()));
        }
        if (q.minRating() != null) {
            all.add(Criteria.where("rating").gte(q.minRating()));
        }
        if (q.inStockOnly()) {
            all.add(Criteria.where("stock").gt(0));
        }
        if (q.text() != null && !q.text().isBlank()) {
            // Every word must match the name, brand or a tag. Pattern.quote => matched literally, never as a regex.
            Arrays.stream(q.text().trim().split("\\s+")).limit(MAX_SEARCH_TOKENS).forEach(token -> {
                String re = Pattern.quote(token);
                all.add(new Criteria().orOperator(
                        Criteria.where("name").regex(re, "i"),
                        Criteria.where("brand").regex(re, "i"),
                        Criteria.where("tags").regex(re, "i")));
            });
        }
        Query query = new Query();
        if (!all.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(all));
        }
        return query;
    }

    public Product get(String id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Product"));
    }

    public List<Product> findAllById(Collection<String> ids) {
        return repository.findAllById(ids);
    }

    public List<Product> related(String id, int limit) {
        Product p = get(id);
        Query query = new Query(Criteria.where("category").is(p.category()).and("_id").ne(p.id()))
                .with(Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("ratingCount"))).limit(limit);
        return mongo.find(query, Product.class);
    }

    public Meta meta() {
        List<Product> all = repository.findAll();
        Map<Category, List<Product>> byCategory = all.stream().collect(Collectors.groupingBy(Product::category));
        List<CategoryInfo> categories = Arrays.stream(Category.values())
                .filter(byCategory::containsKey)
                .map(c -> new CategoryInfo(c.name(), c.label(), c.emoji(), c.group(), byCategory.get(c).size(),
                        byCategory.get(c).stream().map(Product::brand)
                                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)))
                                .stream().toList()))
                .toList();
        long min = all.stream().mapToLong(Product::pricePaise).min().orElse(0);
        long max = all.stream().mapToLong(Product::pricePaise).max().orElse(0);
        return new Meta(categories, min, max);
    }

    public List<Product> all() {
        return repository.findAll().stream().sorted(Comparator.comparing(Product::id)).toList();
    }
}
