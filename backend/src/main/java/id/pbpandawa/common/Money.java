package id.pbpandawa.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rupiah helpers. All amounts are whole rupiah (scale 0). */
public final class Money {

    private Money() {
    }

    public static BigDecimal rupiah(long value) {
        return BigDecimal.valueOf(value);
    }

    public static BigDecimal normalize(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(0, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal times(BigDecimal price, int quantity) {
        return normalize(price).multiply(BigDecimal.valueOf(quantity));
    }

    public static boolean isWholeRupiah(BigDecimal value) {
        return value != null && value.stripTrailingZeros().scale() <= 0;
    }
}
