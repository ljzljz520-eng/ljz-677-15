package com.excel.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateKeyUtilsTest {

    private final LocalDate d = LocalDate.of(2026, 9, 27);

    @Test
    void sameFourElementsProduceSameKey() {
        String k1 = DuplicateKeyUtils.buildKey(" yb001 ", d, "xm01", new BigDecimal("100"));
        String k2 = DuplicateKeyUtils.buildKey("YB001", d, "XM01", new BigDecimal("100.00"));
        assertEquals(k1, k2, "医保编号大小写/空格、金额精度差异应视为重复");
    }

    @Test
    void differentAmountProducesDifferentKey() {
        String k1 = DuplicateKeyUtils.buildKey("YB001", d, "XM01", new BigDecimal("100.00"));
        String k2 = DuplicateKeyUtils.buildKey("YB001", d, "XM01", new BigDecimal("100.01"));
        assertNotEquals(k1, k2, "金额不同不应判重");
    }

    @Test
    void differentVisitDateProducesDifferentKey() {
        String k1 = DuplicateKeyUtils.buildKey("YB001", LocalDate.of(2026, 9, 27), "XM01", new BigDecimal("100"));
        String k2 = DuplicateKeyUtils.buildKey("YB001", LocalDate.of(2026, 9, 28), "XM01", new BigDecimal("100"));
        assertNotEquals(k1, k2, "就诊日期不同不应判重");
    }

    @Test
    void missingElementReturnsNull() {
        assertNull(DuplicateKeyUtils.buildKey(null, d, "XM01", new BigDecimal("100")));
        assertNull(DuplicateKeyUtils.buildKey("YB001", null, "XM01", new BigDecimal("100")));
        assertNull(DuplicateKeyUtils.buildKey("YB001", d, null, new BigDecimal("100")));
        assertNull(DuplicateKeyUtils.buildKey("YB001", d, "XM01", null));
    }

    @Test
    void keyContainsAllFourNormalizedParts() {
        String key = DuplicateKeyUtils.buildKey("yb001", d, "xm01", new BigDecimal("88.5"));
        assertEquals("YB001|2026-09-27|XM01|88.50", key);
    }
}
