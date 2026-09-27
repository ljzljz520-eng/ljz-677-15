package com.excel;

import com.excel.dto.DuplicateItemDTO;
import com.excel.entity.ExcelData;
import com.excel.mapper.ExcelDataMapper;
import com.excel.service.DuplicateCheckService;
import com.excel.utils.DuplicateKeyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateCheckTest {

    @Mock
    private ExcelDataMapper excelDataMapper;

    @InjectMocks
    private DuplicateCheckService duplicateCheckService;

    private ExcelData buildData(String miNo, String visitDate, String itemCode, String amount) {
        ExcelData data = new ExcelData();
        data.setDataCode("DC001");
        data.setName("张三");
        data.setMedicalInsuranceNo(miNo);
        data.setVisitDate(visitDate);
        data.setItemCode(itemCode);
        data.setAmount(amount == null ? null : new BigDecimal(amount));
        data.setDuplicateType(0);
        return data;
    }

    @Test
    void testKeyNormalization() {
        // 相同语义的键应相等：金额 100.00 vs 100.0、日期 2026/9/1 vs 2026-09-01、首尾空格
        String key1 = DuplicateKeyUtils.buildKey("MI001", "2026-09-01", "ITEM01", new BigDecimal("100.00"));
        String key2 = DuplicateKeyUtils.buildKey(" MI001 ", "2026/9/1", "ITEM01", new BigDecimal("100.0"));
        assertEquals(key1, key2);

        // 不同键不相等
        String key3 = DuplicateKeyUtils.buildKey("MI001", "2026-09-02", "ITEM01", new BigDecimal("100.00"));
        assertNotEquals(key1, key3);

        // 日期归一化
        assertEquals("2026-09-01", DuplicateKeyUtils.normalizeVisitDate("2026/9/1"));
        assertEquals("2026-09-01", DuplicateKeyUtils.normalizeVisitDate("2026-09-01 00:00:00"));
        assertNull(DuplicateKeyUtils.normalizeVisitDate("not-a-date"));
    }

    @Test
    void testInFileDuplicate() {
        when(excelDataMapper.selectReportedRecords()).thenReturn(new ArrayList<>());

        List<ExcelData> validList = new ArrayList<>();
        validList.add(buildData("MI001", "2026-09-01", "ITEM01", "100.00"));
        validList.add(buildData("MI001", "2026-09-01", "ITEM01", "100.00")); // 文件内重复
        validList.add(buildData("MI002", "2026-09-01", "ITEM01", "100.00")); // 正常

        DuplicateCheckService.DuplicateCheckResult result = duplicateCheckService.check(validList);

        assertEquals(1, result.fileDuplicateList().size());
        assertEquals(0, result.historyDuplicateList().size());
        assertEquals(0, validList.get(0).getDuplicateType());
        assertEquals(1, validList.get(1).getDuplicateType());
        assertEquals(0, validList.get(2).getDuplicateType());
    }

    @Test
    void testHistoryReportedDuplicate() {
        // 模拟历史批次已成功上送的记录
        ExcelData reported = buildData("MI001", "2026-09-01", "ITEM01", "100.00");
        reported.setBatchNo("HISTORY_BATCH_001");
        when(excelDataMapper.selectReportedRecords()).thenReturn(List.of(reported));

        List<ExcelData> validList = new ArrayList<>();
        validList.add(buildData("MI001", "2026-09-01", "ITEM01", "100.00")); // 历史已上送
        validList.add(buildData("MI001", "2026-09-01", "ITEM01", "100.00")); // 同样命中历史（历史优先）
        validList.add(buildData("MI003", "2026-09-01", "ITEM01", "200.00")); // 正常

        DuplicateCheckService.DuplicateCheckResult result = duplicateCheckService.check(validList);

        assertEquals(0, result.fileDuplicateList().size());
        assertEquals(2, result.historyDuplicateList().size());
        assertEquals(2, validList.get(0).getDuplicateType());
        assertEquals(2, validList.get(1).getDuplicateType());
        assertEquals(0, validList.get(2).getDuplicateType());

        DuplicateItemDTO item = result.historyDuplicateList().get(0);
        assertEquals("HISTORY_BATCH_001", item.getRelatedBatchNo());
    }

    @Test
    void testAmountAndDateVariantsDetectedAsDuplicate() {
        when(excelDataMapper.selectReportedRecords()).thenReturn(new ArrayList<>());

        List<ExcelData> validList = new ArrayList<>();
        validList.add(buildData("MI001", "2026-09-01", "ITEM01", "100.00"));
        // 金额写法不同（100.0）、日期写法不同（已归一化为相同），应判定为文件内重复
        validList.add(buildData("MI001", "2026-09-01", "ITEM01", "100.0"));

        DuplicateCheckService.DuplicateCheckResult result = duplicateCheckService.check(validList);

        assertEquals(1, result.fileDuplicateList().size());
        assertEquals(1, validList.get(1).getDuplicateType());
    }
}
