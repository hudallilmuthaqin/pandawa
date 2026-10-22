package id.pbpandawa.security;

import id.pbpandawa.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * "Device cookies" (OWASP): after a successful login the browser gets a signed cookie bound to that username.
 * Login attempts carrying a valid cookie are throttled per device instead of per username, so an attacker
 * hammering a username cannot lock its real owner out. Client IPs are not usable for this: behind Docker
 * Desktop / the tunnel every request arrives from the same address.
 */
@Service
public class DeviceCookieService {

    public static final String COOKIE_NAME = "pandawa_device";
    private static final String COOKIE_PATH = "/api/auth/login";
    private static final Duration TTL = Duration.ofDays(180);
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final SecretKeySpec key;
    private final Clock clock;

    public DeviceCookieService(AppProperties props, Clock clock) {
        this.key = new SecretKeySpec(props.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.clock = clock;
    }

    /** Cookie for {@code username}, format {@code deviceId.expiresEpochSecond.signature}. */
    public ResponseCookie issue(String username, boolean secure) {
        String deviceId = UUID.randomUUID().toString();
        long exp = Instant.now(clock).plus(TTL).getEpochSecond();
        String value = deviceId + "." + exp + "." + sign(username, deviceId, exp);
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(TTL)
                .build();
    }

    /** The device id when {@code cookie} is an unexpired cookie issued for {@code username}. */
    public Optional<String> verify(String cookie, String username) {
        if (cookie == null) {
            return Optional.empty();
        }
        String[] parts = cookie.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        long exp;
        try {
            exp = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (exp <= Instant.now(clock).getEpochSecond()) {
            return Optional.empty();
        }
        byte[] expected = sign(username, parts[0], exp).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, parts[2].getBytes(StandardCharsets.UTF_8))
                ? Optional.of(parts[0]) : Optional.empty();
    }

    private String sign(String username, String deviceId, long exp) {
        String data = username.trim().toLowerCase(Locale.ROOT) + "|" + deviceId + "|" + exp;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return B64.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
