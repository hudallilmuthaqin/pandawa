package id.pbpandawa.security;

import id.pbpandawa.config.AppProperties;
import id.pbpandawa.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttl = props.jwt().ttl() != null ? props.jwt().ttl() : Duration.ofHours(12);
        this.issuer = props.jwt().issuer() != null ? props.jwt().issuer() : "pb-pandawa";
        this.clock = clock;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public record TokenClaims(Long userId, int tokenVersion) {
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now(clock);
        Instant exp = now.plus(ttl);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("ver", user.getTokenVersion())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new IssuedToken(token, exp);
    }

    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(Instant.now(clock)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Integer ver = claims.get("ver", Integer.class);
            return Optional.of(new TokenClaims(Long.valueOf(claims.getSubject()), ver == null ? -1 : ver));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
