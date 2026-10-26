package com.shubham.lifestylestore.domain;

/** Catalogue departments. The group drives navigation; the emoji is used for placeholder artwork. */
public enum Category {
    MOBILES("Mobiles", "📱", "Electronics"),
    LAPTOPS("Laptops", "💻", "Electronics"),
    AUDIO("Audio & Headphones", "🎧", "Electronics"),
    SMARTWATCHES("Smartwatches", "⌚", "Electronics"),
    CAMERAS("Cameras", "📷", "Electronics"),
    MENS_CLOTHING("Men's Clothing", "👔", "Fashion"),
    WOMENS_CLOTHING("Women's Clothing", "👗", "Fashion"),
    FOOTWEAR("Footwear", "👟", "Fashion"),
    WATCHES("Watches", "⌚", "Fashion"),
    BAGS("Bags & Luggage", "🎒", "Fashion"),
    HOME_KITCHEN("Home & Kitchen", "🏠", "Home & Living"),
    BEAUTY("Beauty & Grooming", "💄", "Home & Living"),
    SPORTS("Sports & Fitness", "🏋️", "Lifestyle"),
    BOOKS("Books", "📚", "Lifestyle");

    private final String label;
    private final String emoji;
    private final String group;

    Category(String label, String emoji, String group) {
        this.label = label;
        this.emoji = emoji;
        this.group = group;
    }

    public String label() {
        return label;
    }

    public String emoji() {
        return emoji;
    }

    public String group() {
        return group;
    }
}
