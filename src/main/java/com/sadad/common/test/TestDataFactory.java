package com.sadad.common.test;

import com.sadad.common.core.money.MoneyUtil;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public final class TestDataFactory {

    private TestDataFactory() {}

    public static Map<String, Object> createStandardTenant() {
        Map<String, Object> map = new HashMap<>();
        map.put("tenantCode", "rawabi-logistics");
        map.put("crNumber", "1010574823");
        map.put("nameEn", "Rawabi Logistics Co.");
        map.put("nameAr", "شركة روابي اللوجستية");
        map.put("city", "Riyadh");
        map.put("iban", "SA44 8000 0204 6080 1592 7411");
        return map;
    }

    public static BigDecimal standardBalance() {
        return MoneyUtil.of(18050.00);
    }
}