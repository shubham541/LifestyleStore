package com.shubham.lifestylestore;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/** End-to-end API flow against a real MongoDB. Skipped automatically when Docker is not available. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class StoreFlowTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

    @Autowired
    MockMvc mvc;

    private static final String SIGNUP = """
            {"name":"Test User","email":"flow@example.com","password":"a-long-password-1",
             "contact":"9876543210","city":"Pune","address":"12 Test Street"}""";

    @Test
    void fullShoppingFlow() throws Exception {
        // catalogue is public, seeded and searchable
        mvc.perform(get("/api/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems", greaterThan(90)));
        mvc.perform(get("/api/products/meta")).andExpect(jsonPath("$.categories", hasSize(14)));
        mvc.perform(get("/api/products").param("category", "MOBILES").param("maxPrice", "30000")
                        .param("sort", "RATING"))
                .andExpect(jsonPath("$.items[0].category").value("MOBILES"));
        mvc.perform(get("/api/products").param("q", "sony headphones"))
                .andExpect(jsonPath("$.items[0].id").value("aud-sony-wh-1000xm5"));
        mvc.perform(get("/api/products/aud-sony-wh-1000xm5/related")).andExpect(jsonPath("$", hasSize(4)));
        // regex metacharacters in search are treated literally
        mvc.perform(get("/api/products").param("q", ".*")).andExpect(jsonPath("$.totalItems").value(0));

        // protected resources require a token
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + forgedAlgNoneToken()))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isConflict());

        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"flow@example.com\",\"password\":\"a-long-password-1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + JsonPath.read(body, "$.accessToken");

        // 2 x Cosco rope (Rs 249) = Rs 498: below the Rs 499 threshold, so Rs 49 shipping applies
        mvc.perform(post("/api/cart/items").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"spo-cosco-skipping-rope\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotalPaise").value(2 * 249_00))
                .andExpect(jsonPath("$.shippingPaise").value(49_00))
                .andExpect(jsonPath("$.totalPaise").value(2 * 249_00 + 49_00))
                .andExpect(jsonPath("$.savingsPaise").value(2 * 150_00));

        mvc.perform(post("/api/orders").header("Authorization", auth)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPaise").value(2 * 249_00 + 49_00))
                .andExpect(jsonPath("$.status").value("PLACED"));
        mvc.perform(get("/api/cart").header("Authorization", auth)).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/orders").header("Authorization", auth)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/products/spo-cosco-skipping-rope")).andExpect(jsonPath("$.stock").value(158));

        // assistant works (offline fallback when no API key is configured), including multi-turn + product context
        mvc.perform(post("/api/assistant/chat").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"best rated phones under 30000"}]}"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("fallback"))
                .andExpect(jsonPath("$.products[0].category").value("MOBILES"));
        mvc.perform(post("/api/assistant/chat").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"assistant","content":"hi"}]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void errorsDoNotLeakInternals() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("Exception"))));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"nope\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
        mvc.perform(get("/api/products").param("sort", "DROP_TABLE")).andExpect(status().isBadRequest());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/products")).andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("X-Request-Id"));
    }

    /** {"alg":"none"} header, subject=anyone: must never be accepted. */
    private static String forgedAlgNoneToken() {
        var enc = java.util.Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String payload = enc.encodeToString(
                "{\"sub\":\"victim\",\"iss\":\"lifestylestore\",\"exp\":4102444800,\"roles\":[\"USER\"]}".getBytes());
        return header + "." + payload + ".";
    }
}
