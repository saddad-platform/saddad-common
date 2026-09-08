package com.sadad.common.core.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility for financial amounts in Saudi Riyal (SAR).
 * Adheres strictly to rule: No floating-point arithmetic for currency.
 */
public final class MoneyUtil {

    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final int SCALE = 2;

    private MoneyUtil() {}

    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(long value) {
        return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(String value) {
        return new BigDecimal(value.trim()).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(BigDecimal value) {
        if (value == null) return zero();
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.add(b).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.subtract(b).setScale(SCALE, ROUNDING);
    }

    public static boolean isGreaterThan(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.compareTo(b) > 0;
    }

    public static boolean isLessThan(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.compareTo(b) < 0;
    }

    public static boolean isPositive(BigDecimal a) {
        return a != null && a.compareTo(BigDecimal.ZERO) > 0;
    }
}
