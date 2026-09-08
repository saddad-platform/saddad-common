package com.sadad.common.core;

import com.sadad.common.core.util.MaskingUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaskingUtilTest {

    @Test
    void testMaskCr() {
        assertEquals("10******23", MaskingUtil.maskCr("1010574823"));
        assertEquals("****", MaskingUtil.maskCr(null));
    }

    @Test
    void testMaskNationalId() {
        assertEquals("24******93", MaskingUtil.maskNationalId("2412778093"));
    }

    @Test
    void testMaskIban() {
        String masked = MaskingUtil.maskIban("SA44 8000 0204 6080 1592 7411");
        assertTrue(masked.startsWith("SA44"));
        assertTrue(masked.endsWith("7411"));
        assertTrue(masked.contains("****"));
    }

    @Test
    void testMaskEmail() {
        assertEquals("co******@rawabi.sa", MaskingUtil.maskEmail("compliance@rawabi.sa"));
    }
}