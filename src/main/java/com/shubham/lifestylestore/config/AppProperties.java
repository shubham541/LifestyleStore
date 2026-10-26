package com.shubham.lifestylestore.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed, validated configuration. A missing or weak JWT secret aborts startup (fail securely). */
@Validated
@ConfigurationProperties("app")
public record AppProperties(@Valid @NotNull Security security, @Valid @NotNull Assistant assistant) {

    public record Security(
            @NotBlank @Size(min = 32, message = "JWT_SECRET must be at least 32 characters") String jwtSecret,
            @Min(1) int jwtTtlMinutes,
            @Min(1) int authRateLimitPerMinute) {
    }

    public record Assistant(String apiKey, @NotBlank String model, @NotBlank String baseUrl,
                            @Min(1) int rateLimitPerMinute) {
        public boolean claudeEnabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
