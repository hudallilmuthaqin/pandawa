package id.pbpandawa.report;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/**
 * Splits a whole-rupiah amount over N players so that the parts always add
 * up to the exact amount. Each player gets floor(amount / N); the remainder
 * (at most N-1 rupiah) is given one rupiah each to the first players in
 * ascending player-id order (deterministic and documented).
 */
public final class CostAllocator {

    private CostAllocator() {
    }

    public static Map<Long, BigDecimal> splitEqually(BigDecimal amount, SortedSet<Long> playerIds) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        if (playerIds.isEmpty()) {
            return result;
        }
        long total = amount.longValueExact();
        int n = playerIds.size();
        long base = Math.floorDiv(total, n);
        long remainder = total - base * n;
        List<Long> ordered = new ArrayList<>(playerIds);
        for (int i = 0; i < n; i++) {
            result.put(ordered.get(i), BigDecimal.valueOf(base + (i < remainder ? 1 : 0)));
        }
        return result;
    }
}
