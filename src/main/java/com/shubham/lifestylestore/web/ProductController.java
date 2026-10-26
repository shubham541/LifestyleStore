package com.shubham.lifestylestore.web;

import com.shubham.lifestylestore.domain.Category;
import com.shubham.lifestylestore.domain.Product;
import com.shubham.lifestylestore.domain.ProductQuery;
import com.shubham.lifestylestore.domain.ProductQuery.SortOrder;
import com.shubham.lifestylestore.service.ProductService;
import com.shubham.lifestylestore.service.ProductService.Meta;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Products")
@Validated
@RestController
@RequestMapping("/api/products")
public class ProductController {

    public record ProductPage(List<Product> items, int page, int totalPages, long totalItems) {
    }

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    /** Prices in the query string are whole rupees; the API otherwise speaks paise. */
    @GetMapping
    public ProductPage list(@RequestParam(required = false) Category category,
                            @RequestParam(name = "q", required = false) @Size(max = 60) String q,
                            @RequestParam(required = false) @Min(0) @Max(10_000_000) Long minPrice,
                            @RequestParam(required = false) @Min(0) @Max(10_000_000) Long maxPrice,
                            @RequestParam(required = false) @Min(0) @Max(5) Double minRating,
                            @RequestParam(required = false) @Size(max = 40) String brand,
                            @RequestParam(defaultValue = "false") boolean inStock,
                            @RequestParam(defaultValue = "RELEVANCE") SortOrder sort,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "12") int size) {
        var query = new ProductQuery(category, q, minPrice == null ? null : minPrice * 100,
                maxPrice == null ? null : maxPrice * 100, minRating, brand, inStock, sort);
        Page<Product> result = products.search(query, page, size);
        return new ProductPage(result.getContent(), result.getNumber(), result.getTotalPages(),
                result.getTotalElements());
    }

    @GetMapping("/meta")
    public Meta meta() {
        return products.meta();
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable String id) {
        return products.get(id);
    }

    @GetMapping("/{id}/related")
    public List<Product> related(@PathVariable String id) {
        return products.related(id, 4);
    }
}
