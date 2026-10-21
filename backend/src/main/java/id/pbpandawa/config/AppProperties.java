package id.pbpandawa.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Seed seed) {

    public record Jwt(@NotBlank String secret, Duration ttl, String issuer) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /** Demo seed data. Never enable in production. */
    public record Seed(boolean enabled, String adminPassword, String userPassword) {
    }
}
