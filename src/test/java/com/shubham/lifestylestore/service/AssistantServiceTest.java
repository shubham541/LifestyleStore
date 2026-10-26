package com.shubham.lifestylestore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shubham.lifestylestore.config.AppProperties;
import com.shubham.lifestylestore.domain.Category;
import com.shubham.lifestylestore.domain.Product;
import com.shubham.lifestylestore.domain.ProductQuery;
import com.shubham.lifestylestore.domain.ProductQuery.SortOrder;
import com.shubham.lifestylestore.service.AssistantService.Turn;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import tools.jackson.databind.json.JsonMapper;

class AssistantServiceTest {

    private static final Set<String> BRANDS = Set.of("sony", "apple", "jbl", "puma");

    private final ProductService products = mock(ProductService.class);

    private AssistantService service() {
        var props = new AppProperties(
                new AppProperties.Security("x".repeat(32), 60, 10),
                new AppProperties.Assistant("", "claude-haiku-5-5", "http://localhost:0", 5));
        return new AssistantService(products, props, JsonMapper.builder().build());
    }

    private static Product product(String id, Category category, long rupees) {
        return new Product(id, "Name " + id, "Sony", "desc", category, rupees * 100, rupees * 120, 17, 4.5, 100,
                List.of("tag"), 5, null, null);
    }

    // --- natural-language fallback parser ---

    @Test
    void parsesCategoryBudgetWithKSuffixAndRatingSort() {
        ProductQuery q = AssistantService.parse("Best rated phones under 30k", BRANDS);

        assertThat(q.category()).isEqualTo(Category.MOBILES);
        assertThat(q.maxPaise()).isEqualTo(30_000_00);
        assertThat(q.sort()).isEqualTo(SortOrder.RATING);
    }

    @Test
    void topRatedIsNotMistakenForWomensTops() {
        assertThat(AssistantService.parse("top rated laptop", BRANDS).category()).isEqualTo(Category.LAPTOPS);
    }

    @Test
    void smartWatchBeatsPlainWatch() {
        assertThat(AssistantService.parse("a smart watch for running", BRANDS).category())
                .isEqualTo(Category.SMARTWATCHES);
        assertThat(AssistantService.parse("a leather watch", BRANDS).category()).isEqualTo(Category.WATCHES);
    }

    @Test
    void parsesBrandBudgetRangeAndCheapestSort() {
        ProductQuery q = AssistantService.parse("cheapest sony headphones above Rs. 5,000 under 20000", BRANDS);

        assertThat(q.brand()).isEqualTo("sony");
        assertThat(q.category()).isEqualTo(Category.AUDIO);
        assertThat(q.minPaise()).isEqualTo(5_000_00);
        assertThat(q.maxPaise()).isEqualTo(20_000_00);
        assertThat(q.sort()).isEqualTo(SortOrder.PRICE_ASC);
    }

    @Test
    void fallsBackToFreeTextWhenNoDepartmentOrBrandRecognised() {
        ProductQuery q = AssistantService.parse("I want something with noise cancelling", BRANDS);

        assertThat(q.category()).isNull();
        assertThat(q.text()).isEqualTo("noise cancelling");
    }

    // --- tools (inputs come from the model and are untrusted) ---

    @Test
    void searchToolClampsAndConvertsInputs() {
        when(products.search(any(ProductQuery.class), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(product("mob-1", Category.MOBILES, 20_000))));

        String json = service().executeTool("search_products",
                Map.of("category", "mobiles", "max_price", 25000, "min_rating", 99, "limit", 500, "sort", "BOGUS"));

        var query = ArgumentCaptor.forClass(ProductQuery.class);
        var size = ArgumentCaptor.forClass(Integer.class);
        verify(products).search(query.capture(), anyInt(), size.capture());
        assertThat(size.getValue()).isEqualTo(8);
        assertThat(query.getValue().category()).isEqualTo(Category.MOBILES);
        assertThat(query.getValue().maxPaise()).isEqualTo(25_000_00);
        assertThat(query.getValue().minRating()).isEqualTo(5.0);
        assertThat(query.getValue().sort()).isEqualTo(SortOrder.RELEVANCE);
        assertThat(query.getValue().inStockOnly()).isTrue();
        assertThat(json).contains("\"id\":\"mob-1\"").contains("\"price_inr\":20000");
    }

    @Test
    void unknownToolAndBadInputNeverThrow() {
        assertThat(service().executeTool("drop_database", Map.of())).contains("unknown tool");
        assertThat(service().executeTool("get_product_details", Map.of("id", 42))).contains("not found");
        assertThat(service().executeTool(null, Map.of())).contains("unknown tool");
    }

    @Test
    void compareToolReturnsDetailsForRequestedIds() {
        when(products.findAllById(any())).thenReturn(List.of(product("a", Category.AUDIO, 1000),
                product("b", Category.AUDIO, 2000)));

        String json = service().executeTool("compare_products", Map.of("ids", List.of("a", "b", 7)));

        assertThat(json).contains("\"id\":\"a\"").contains("\"id\":\"b\"").contains("description");
    }

    // --- conversation handling ---

    @Test
    void historyMustStartWithUserAndEndWithUser() {
        var cleaned = AssistantService.sanitise(List.of(new Turn("assistant", "hi"), new Turn("user", "phones"),
                new Turn("assistant", "ok"), new Turn("user", "cheaper?")));
        assertThat(cleaned).extracting(Turn::role).containsExactly("user", "assistant", "user");

        assertThatThrownBy(() -> AssistantService.sanitise(List.of(new Turn("user", "x"), new Turn("assistant", "y"))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void parsesFollowUpSuggestions() {
        var text = "Great picks!\nSUGGESTIONS: Compare them | Under 20000? | Any deals | a fourth | ";

        assertThat(AssistantService.parseSuggestions(text)).containsExactly("Compare them", "Under 20000?", "Any deals");
        assertThat(AssistantService.parseSuggestions("no suggestions here")).isEmpty();
    }

    @Test
    void chatWithoutApiKeyUsesFallbackAndCatalogue() {
        when(products.all()).thenReturn(List.of(product("mob-1", Category.MOBILES, 20_000)));
        when(products.search(any(ProductQuery.class), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(product("mob-1", Category.MOBILES, 20_000))));

        var reply = service().chat(List.of(new Turn("user", "best phone under 30000")), null);

        assertThat(reply.source()).isEqualTo("fallback");
        assertThat(reply.products()).extracting(Product::id).containsExactly("mob-1");
        assertThat(reply.suggestions()).isNotEmpty();
    }
}
