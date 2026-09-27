package com.excel.converter;

import cn.hutool.core.date.DateUtil;
import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.converters.ReadConverterContext;
import com.alibaba.excel.converters.WriteConverterContext;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.data.WriteCellData;

import java.util.Date;

/**
 * 就诊日期自定义转换器
 * 读取时兼容两种填写方式：
 * 1. 文本单元格："2026-09-01"、"2026/9/1" 等，原样返回由后续归一化处理
 * 2. 日期型单元格：Excel日期序列号，转换为 yyyy-MM-dd 字符串
 * 写入时统一输出文本单元格
 */
public class FlexibleDateConverter implements Converter<String> {

    @Override
    public Class<?> supportJavaTypeKey() {
        return String.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public String convertToJavaData(ReadConverterContext<?> context) {
        CellDataTypeEnum cellType = context.getReadCellData().getType();
        if (cellType == CellDataTypeEnum.NUMBER) {
            // 日期型单元格：Excel日期序列号转日期字符串
            double serialNumber = context.getReadCellData().getNumberValue().doubleValue();
            Date date = org.apache.poi.ss.usermodel.DateUtil.getJavaDate(serialNumber);
            return DateUtil.format(date, "yyyy-MM-dd");
        }
        // 文本单元格：原样返回
        return context.getReadCellData().getStringValue();
    }

    @Override
    public WriteCellData<?> convertToExcelData(WriteConverterContext<String> context) {
        String value = context.getValue();
        return new WriteCellData<>(value == null ? "" : value);
    }
}
