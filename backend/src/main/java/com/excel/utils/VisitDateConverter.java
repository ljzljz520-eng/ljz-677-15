package com.excel.utils;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 就诊日期转换器
 * 兼容：Excel真实日期单元格、Excel日期序列号数字、yyyy-MM-dd、yyyy/M/d、yyyy.M.d、yyyyMMdd 等文本
 * 无法解析时返回null，由 ValidationUtils 提示“就诊日期不能为空或格式不正确”
 */
public class VisitDateConverter implements Converter<LocalDate> {

    private static final List<DateTimeFormatter> TEXT_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    @Override
    public Class<?> supportJavaTypeKey() {
        return LocalDate.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public LocalDate convertToJavaData(ReadCellData<?> cellData,
                                       ExcelContentProperty contentProperty,
                                       GlobalConfiguration globalConfiguration) {
        if (cellData == null || cellData.getType() == null) {
            return null;
        }

        // 1. Excel中真正的日期单元格：EasyExcel已将其解析为 LocalDate/LocalDateTime/Date 放在 data 中
        Object data = cellData.getData();
        LocalDate fromData = toLocalDate(data);
        if (fromData != null) {
            return fromData;
        }

        // 2. 文本单元格
        String text = cellData.getStringValue();
        if (text != null) {
            LocalDate parsed = parseText(text.trim());
            if (parsed != null) {
                return parsed;
            }
        }

        // 3. 数字单元格（未设置日期格式时，data可能为空，值在 numberValue）
        BigDecimal number = cellData.getNumberValue();
        if (number != null) {
            return parseNumber(number);
        }

        return null;
    }

    private LocalDate toLocalDate(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof LocalDate localDate) {
            return localDate;
        }
        if (data instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (data instanceof Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }

    private LocalDate parseText(String trimmed) {
        if (trimmed.isEmpty()) {
            return null;
        }

        // 纯数字文本：可能是Excel日期序列号，也可能是yyyyMMdd
        if (trimmed.matches("\\d+(\\.0+)?")) {
            try {
                return parseNumber(new BigDecimal(trimmed));
            } catch (Exception ignore) {
                return null;
            }
        }

        for (DateTimeFormatter formatter : TEXT_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (Exception ignore) {
                // 尝试下一种格式
            }
        }
        return null;
    }

    private LocalDate parseNumber(BigDecimal number) {
        // Excel日期序列号范围约2~2958465（1900~9999年）；yyyyMMdd为8位整数（>=20000101）
        String compact = number.stripTrailingZeros().toPlainString().replace(".0", "");
        if (number.compareTo(new BigDecimal("2958465")) <= 0) {
            Date date = org.apache.poi.ss.usermodel.DateUtil.getJavaDate(number.doubleValue());
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (compact.length() == 8) {
            return LocalDate.parse(compact, DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        return null;
    }
}
