package com.excel;

import com.alibaba.excel.EasyExcel;
import com.excel.dto.ExcelDataDTO;
import com.excel.dto.ImportResultDTO;
import com.excel.dto.PrecheckResultDTO;
import com.excel.entity.ExcelData;
import com.excel.entity.User;
import com.excel.mapper.ExcelDataMapper;
import com.excel.mapper.ImportRecordMapper;
import com.excel.mapper.UserMapper;
import com.excel.service.DuplicateCheckService;
import com.excel.service.ExcelImportService;
import com.excel.service.PrecheckCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 预检-确认导入完整流程集成测试
 * 使用真实的 DuplicateCheckService 和 PrecheckCache，mock 数据库层
 */
class PrecheckFlowIntegrationTest {

    private ExcelDataMapper excelDataMapper;
    private ImportRecordMapper importRecordMapper;
    private UserMapper userMapper;
    private ExcelImportService excelImportService;

    @BeforeEach
    void setUp() {
        excelDataMapper = mock(ExcelDataMapper.class);
        importRecordMapper = mock(ImportRecordMapper.class);
        userMapper = mock(UserMapper.class);
        DuplicateCheckService duplicateCheckService = new DuplicateCheckService(excelDataMapper);
        PrecheckCache precheckCache = new PrecheckCache();
        excelImportService = new ExcelImportService(
                excelDataMapper, importRecordMapper, userMapper, duplicateCheckService, precheckCache);

        User operator = new User();
        operator.setId(1L);
        operator.setRealName("测试员");
        when(userMapper.selectById(1L)).thenReturn(operator);
    }

    private ExcelDataDTO buildRow(String dataCode, String name, String miNo,
                                  String visitDate, String itemCode, String amount) {
        ExcelDataDTO dto = new ExcelDataDTO();
        dto.setDataCode(dataCode);
        dto.setName(name);
        dto.setAmount(amount == null ? null : new BigDecimal(amount));
        dto.setMedicalInsuranceNo(miNo);
        dto.setVisitDate(visitDate);
        dto.setItemCode(itemCode);
        return dto;
    }

    private MockMultipartFile buildExcelFile(List<ExcelDataDTO> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, ExcelDataDTO.class).sheet("sheet1").doWrite(rows);
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray());
    }

    @Test
    void testPrecheckAndConfirmFlow() throws Exception {
        // 构造测试数据：1条文件内重复、1条历史已上送、1条校验失败、2条正常
        List<ExcelDataDTO> rows = new ArrayList<>();
        rows.add(buildRow("DC001", "张三", "MI001", "2026-09-01", "ITEM01", "100.00")); // 正常
        rows.add(buildRow("DC002", "张三", "MI001", "2026-09-01", "ITEM01", "100.00")); // 文件内重复
        rows.add(buildRow("DC003", "李四", "MI002", "2026-09-02", "ITEM02", "200.00")); // 正常
        rows.add(buildRow("DC004", "王五", "MI003", "2026-09-03", "ITEM03", "300.00")); // 历史已上送
        rows.add(buildRow("DC005", "赵六", null, "2026-09-04", "ITEM04", "400.00"));    // 校验失败：医保编号为空

        // 模拟历史已成功上送记录（MI003）
        ExcelData reported = new ExcelData();
        reported.setMedicalInsuranceNo("MI003");
        reported.setVisitDate("2026-09-03");
        reported.setItemCode("ITEM03");
        reported.setAmount(new BigDecimal("300.00"));
        reported.setBatchNo("HISTORY_BATCH");
        when(excelDataMapper.selectReportedRecords()).thenReturn(List.of(reported));

        // ========== 第一步：预检 ==========
        PrecheckResultDTO precheck = excelImportService.precheck(buildExcelFile(rows), 1L);

        assertEquals(5, precheck.getTotalCount());
        assertEquals(2, precheck.getNormalCount());
        assertEquals(1, precheck.getFileDuplicateCount());
        assertEquals(1, precheck.getHistoryDuplicateCount());
        assertEquals(1, precheck.getErrorCount());
        assertTrue(precheck.getHasDuplicates());
        assertEquals("HISTORY_BATCH", precheck.getHistoryDuplicateList().get(0).getRelatedBatchNo());
        // 预检阶段不应写库
        verify(excelDataMapper, never()).insert(any(ExcelData.class));

        // ========== 第二步：确认导入（排除重复） ==========
        ImportResultDTO result = excelImportService.confirmImport(
                precheck.getCheckNo(), "EXCLUDE", 1L);

        assertEquals(2, result.getSuccessCount());
        assertEquals(2, result.getExcludedDuplicateCount());
        assertEquals(1, result.getFailCount()); // 校验失败的1条
        verify(excelDataMapper, times(2)).insert(any(ExcelData.class));
    }

    @Test
    void testConfirmImportIncludeDuplicates() throws Exception {
        List<ExcelDataDTO> rows = new ArrayList<>();
        rows.add(buildRow("DC001", "张三", "MI001", "2026-09-01", "ITEM01", "100.00"));
        rows.add(buildRow("DC002", "张三", "MI001", "2026-09-01", "ITEM01", "100.00")); // 文件内重复

        when(excelDataMapper.selectReportedRecords()).thenReturn(new ArrayList<>());

        PrecheckResultDTO precheck = excelImportService.precheck(buildExcelFile(rows), 1L);
        assertEquals(1, precheck.getFileDuplicateCount());

        // 全部导入：疑似重复记录也入库，且保留重复标记
        ImportResultDTO result = excelImportService.confirmImport(
                precheck.getCheckNo(), "INCLUDE", 1L);

        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getExcludedDuplicateCount());
        verify(excelDataMapper, times(2)).insert(any(ExcelData.class));
    }

    @Test
    void testConfirmWithInvalidCheckNo() {
        when(excelDataMapper.selectReportedRecords()).thenReturn(new ArrayList<>());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> excelImportService.confirmImport("not-exist", "EXCLUDE", 1L));
        assertTrue(ex.getMessage().contains("预检数据不存在或已过期"));
    }
}
