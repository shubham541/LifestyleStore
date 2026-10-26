package com.shubham.lifestylestore.domain;

/** Catalogue search criteria shared by the REST API and the AI assistant's tools. */
public record ProductQuery(
        Category category,
        String text,
        Long minPaise,
        Long maxPaise,
        Double minRating,
        String brand,
        boolean inStockOnly,
        SortOrder sort) {

    public enum SortOrder {
        RELEVANCE, PRICE_ASC, PRICE_DESC, RATING, DISCOUNT
    }

    public static ProductQuery all() {
        return new ProductQuery(null, null, null, null, null, null, false, SortOrder.RELEVANCE);
    }

    public ProductQuery withSort(SortOrder newSort) {
        return new ProductQuery(category, text, minPaise, maxPaise, minRating, brand, inStockOnly, newSort);
    }
}
