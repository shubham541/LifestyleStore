package com.shubham.lifestylestore.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private int hit(RateLimitFilter filter, String path, String ip) throws Exception {
        var request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void blocksAuthEndpointAfterLimitPerIp() throws Exception {
        var filter = new RateLimitFilter(3, 5);

        for (int i = 0; i < 3; i++) {
            assertThat(hit(filter, "/api/auth/login", "1.1.1.1")).isEqualTo(200);
        }
        assertThat(hit(filter, "/api/auth/login", "1.1.1.1")).isEqualTo(429);
        assertThat(hit(filter, "/api/auth/login", "2.2.2.2")).isEqualTo(200);
    }

    @Test
    void doesNotLimitOtherPaths() throws Exception {
        var filter = new RateLimitFilter(1, 1);

        for (int i = 0; i < 10; i++) {
            assertThat(hit(filter, "/api/products", "1.1.1.1")).isEqualTo(200);
        }
    }
}
