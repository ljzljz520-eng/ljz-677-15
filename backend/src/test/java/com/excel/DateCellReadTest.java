package com.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.excel.dto.ExcelDataDTO;
import com.excel.entity.ExcelData;
import com.excel.listener.PrecheckDataListener;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证就诊日期列兼容日期型单元格（文本单元格场景由集成测试覆盖）
 */
class DateCellReadTest {

    /**
     * 用于生成测试文件的写入模型（就诊日期为Date类型，会写成日期型单元格）
     */
    @Data
    static class WriteModel {
        @ExcelProperty(value = "数据编号", index = 0)
        private String dataCode;
        @ExcelProperty(value = "姓名", index = 1)
        private String name;
        @ExcelProperty(value = "身份证号", index = 2)
        private String idCard;
        @ExcelProperty(value = "手机号", index = 3)
        private String phone;
        @ExcelProperty(value = "金额", index = 4)
        private BigDecimal amount;
        @ExcelProperty(value = "地址", index = 5)
        private String address;
        @ExcelProperty(value = "备注", index = 6)
        private String remark;
        @ExcelProperty(value = "医保编号", index = 7)
        private String medicalInsuranceNo;
        @ExcelProperty(value = "就诊日期", index = 8)
        private Date visitDate; // Date类型：写成日期型单元格
        @ExcelProperty(value = "项目编码", index = 9)
        private String itemCode;
    }

    @Test
    @SuppressWarnings("deprecation")
    void testReadDateTypeCell() {
        List<WriteModel> rows = new ArrayList<>();

        WriteModel row1 = new WriteModel();
        row1.setDataCode("DC001");
        row1.setName("张三");
        row1.setAmount(new BigDecimal("100.00"));
        row1.setMedicalInsuranceNo("MI001");
        row1.setVisitDate(new Date(126, 8, 1)); // 2026-09-01，日期型单元格
        row1.setItemCode("ITEM01");
        rows.add(row1);

        WriteModel row2 = new WriteModel();
        row2.setDataCode("DC002");
        row2.setName("李四");
        row2.setAmount(new BigDecimal("200.00"));
        row2.setMedicalInsuranceNo("MI002");
        row2.setVisitDate(new Date(126, 8, 15)); // 2026-09-15
        row2.setItemCode("ITEM02");
        rows.add(row2);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, WriteModel.class).sheet("sheet1").doWrite(rows);

        // 用预检监听器解析
        PrecheckDataListener listener = new PrecheckDataListener();
        EasyExcel.read(new ByteArrayInputStream(out.toByteArray()), ExcelDataDTO.class, listener)
                .sheet().headRowNumber(1).doRead();

        List<ExcelData> validList = listener.getValidList();
        assertEquals(2, validList.size(), "日期型单元格应解析成功: " + listener.getErrorList());
        // 应归一化为 yyyy-MM-dd
        assertEquals("2026-09-01", validList.get(0).getVisitDate());
        assertEquals("2026-09-15", validList.get(1).getVisitDate());
    }
}
