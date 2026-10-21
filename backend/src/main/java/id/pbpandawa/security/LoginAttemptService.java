package id.pbpandawa.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Simple in-memory brute-force protection: 5 failures lock a username for 5 minutes. */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);

    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public boolean isLocked(String username) {
        Attempts a = attempts.get(key(username));
        return a != null && a.lockedUntil() != null && a.lockedUntil().isAfter(Instant.now(clock));
    }

    public void onFailure(String username) {
        attempts.compute(key(username), (k, a) -> {
            int failures = (a == null || (a.lockedUntil() != null && !a.lockedUntil().isAfter(Instant.now(clock))))
                    ? 1 : a.failures() + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? Instant.now(clock).plus(LOCK_DURATION) : null;
            return new Attempts(failures, lockedUntil);
        });
    }

    public void onSuccess(String username) {
        attempts.remove(key(username));
    }

    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
