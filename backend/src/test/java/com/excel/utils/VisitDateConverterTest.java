package com.excel.utils;

import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.data.ReadCellData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class VisitDateConverterTest {

    private final VisitDateConverter converter = new VisitDateConverter();

    private LocalDate convert(ReadCellData<?> cell) {
        return converter.convertToJavaData(cell, null, null);
    }

    @Test
    void parsesIsoText() {
        ReadCellData<String> cell = new ReadCellData<>(CellDataTypeEnum.STRING, "2026-09-27");
        assertEquals(LocalDate.of(2026, 9, 27), convert(cell));
    }

    @Test
    void parsesSlashText() {
        ReadCellData<String> cell = new ReadCellData<>(CellDataTypeEnum.STRING, "2026/9/5");
        assertEquals(LocalDate.of(2026, 9, 5), convert(cell));
    }

    @Test
    void parsesCompactText() {
        ReadCellData<String> cell = new ReadCellData<>(CellDataTypeEnum.STRING, "20260927");
        assertEquals(LocalDate.of(2026, 9, 27), convert(cell));
    }

    @Test
    void parsesChineseText() {
        ReadCellData<String> cell = new ReadCellData<>(CellDataTypeEnum.STRING, "2026年9月5日");
        assertEquals(LocalDate.of(2026, 9, 5), convert(cell));
    }

    @Test
    void parsesExcelSerialNumber() {
        // 45927 = 2025-09-27 (Excel 1900 date system)
        ReadCellData<BigDecimal> cell = new ReadCellData<>(new BigDecimal("45927"));
        LocalDate result = convert(cell);
        assertNotNull(result);
        assertEquals(2025, result.getYear());
        assertEquals(9, result.getMonthValue());
        assertEquals(27, result.getDayOfMonth());
    }

    @Test
    void returnsNullForBlank() {
        ReadCellData<String> cell = new ReadCellData<>(CellDataTypeEnum.STRING, "  ");
        assertNull(convert(cell));
    }

    @Test
    void returnsNullForGarbage() {
        ReadCellData<String> cell = new ReadCellData<>(CellDataTypeEnum.STRING, "not-a-date");
        assertNull(convert(cell));
    }
}
