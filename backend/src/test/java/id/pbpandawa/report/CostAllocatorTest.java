package id.pbpandawa.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class CostAllocatorTest {

    @Test
    void splitsEvenAmountsExactly() {
        Map<Long, BigDecimal> parts = CostAllocator.splitEqually(BigDecimal.valueOf(7000), new TreeSet<>(List.of(1L, 2L, 3L, 4L)));
        assertThat(parts.values()).containsOnly(BigDecimal.valueOf(1750));
    }

    @Test
    void remainderIsDistributedSoTotalMatchesTheRealFee() {
        // 7000 / 3 = 2333.33 → 2334 + 2333 + 2333 = 7000
        Map<Long, BigDecimal> parts = CostAllocator.splitEqually(BigDecimal.valueOf(7000), new TreeSet<>(List.of(9L, 3L, 5L)));
        assertThat(parts).containsEntry(3L, BigDecimal.valueOf(2334))
                .containsEntry(5L, BigDecimal.valueOf(2333))
                .containsEntry(9L, BigDecimal.valueOf(2333));
        assertThat(parts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("7000");
    }

    @Test
    void sumAlwaysEqualsAmountForAnyPlayerCount() {
        for (int n = 1; n <= 23; n++) {
            TreeSet<Long> ids = new TreeSet<>();
            for (long i = 1; i <= n; i++) {
                ids.add(i);
            }
            BigDecimal sum = CostAllocator.splitEqually(BigDecimal.valueOf(7001), ids).values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo("7001");
        }
    }

    @Test
    void noPlayersMeansNoAllocation() {
        assertThat(CostAllocator.splitEqually(BigDecimal.valueOf(7000), new TreeSet<>())).isEmpty();
    }
}
