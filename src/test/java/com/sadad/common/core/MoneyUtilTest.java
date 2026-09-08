package com.sadad.common.core;

import com.sadad.common.core.money.MoneyUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyUtilTest {

    @Test
    void testScaleAndRounding() {
        BigDecimal val = new BigDecimal("150.3456");
        BigDecimal scaled = MoneyUtil.of(val);

        assertEquals(new BigDecimal("150.35"), scaled);
        assertEquals(2, scaled.scale());
    }

    @Test
    void testZero() {
        BigDecimal zero = MoneyUtil.zero();
        assertNotNull(zero);
        assertEquals(new BigDecimal("0.00"), zero);
    }

    @Test
    void testAddAndSubtract() {
        BigDecimal a = new BigDecimal("1000.50");
        BigDecimal b = new BigDecimal("250.25");

        assertEquals(new BigDecimal("1250.75"), MoneyUtil.add(a, b));
        assertEquals(new BigDecimal("750.25"), MoneyUtil.subtract(a, b));
    }

    @Test
    void testIsLessThan() {
        assertTrue(MoneyUtil.isLessThan(new BigDecimal("4999.99"), new BigDecimal("5000.00")));
        assertFalse(MoneyUtil.isLessThan(new BigDecimal("5000.00"), new BigDecimal("5000.00")));
    }
}