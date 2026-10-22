package id.pbpandawa.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Simple in-memory brute-force protection: 5 failures lock a bucket for 5 minutes.
 * A bucket is either a username (attempts without a trusted device cookie) or one trusted device,
 * see {@link DeviceCookieService}.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    /** Above this many tracked buckets, expired ones are purged (bounds memory under random usernames). */
    private static final int PURGE_THRESHOLD = 10_000;

    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public static String usernameBucket(String username) {
        return "user:" + (username == null ? "" : username.trim().toLowerCase());
    }

    public static String deviceBucket(String deviceId) {
        return "device:" + deviceId;
    }

    public boolean isLocked(String bucket) {
        Attempts a = attempts.get(bucket);
        return a != null && a.lockedUntil() != null && a.lockedUntil().isAfter(Instant.now(clock));
    }

    public void onFailure(String bucket) {
        if (attempts.size() > PURGE_THRESHOLD) {
            purgeExpired();
        }
        attempts.compute(bucket, (k, a) -> {
            int failures = (a == null || (a.lockedUntil() != null && !a.lockedUntil().isAfter(Instant.now(clock))))
                    ? 1 : a.failures() + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? Instant.now(clock).plus(LOCK_DURATION) : null;
            return new Attempts(failures, lockedUntil);
        });
    }

    public void onSuccess(String bucket) {
        attempts.remove(bucket);
    }

    /** Drops expired locks and failure counts that never reached a lock. */
    private void purgeExpired() {
        Instant now = Instant.now(clock);
        attempts.values().removeIf(a -> a.lockedUntil() == null || !a.lockedUntil().isAfter(now));
    }
}
