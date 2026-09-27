package com.excel.utils;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 重复判断键工具类
 * 重复判断维度：医保编号 + 就诊日期 + 项目编码 + 金额
 */
public class DuplicateKeyUtils {

    private static final String SEPARATOR = "|";

    /**
     * 构建重复判断键
     * 对四个维度分别归一化后拼接，保证语义相同的数据生成相同的键
     */
    public static String buildKey(String medicalInsuranceNo, String visitDate,
                                  String itemCode, BigDecimal amount) {
        return normalizeText(medicalInsuranceNo) + SEPARATOR
                + normalizeVisitDate(visitDate) + SEPARATOR
                + normalizeText(itemCode) + SEPARATOR
                + normalizeAmount(amount);
    }

    /**
     * 文本归一化：去除首尾空白
     */
    private static String normalizeText(String text) {
        return text == null ? "" : text.trim();
    }

    /**
     * 就诊日期归一化为 yyyy-MM-dd 格式
     * 兼容 Excel 中常见的日期写法：yyyy-MM-dd、yyyy/M/d、yyyy.MM.dd、yyyyMMdd、
     * 以及日期型单元格被读取为 "yyyy-MM-dd HH:mm:ss" 的情况
     *
     * @return 归一化后的日期字符串，无法解析时返回 null
     */
    public static String normalizeVisitDate(String visitDate) {
        if (StrUtil.isBlank(visitDate)) {
            return null;
        }
        String trimmed = visitDate.trim();
        try {
            Date date = DateUtil.parse(trimmed);
            return DateUtil.format(date, "yyyy-MM-dd");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 金额归一化：去除末尾多余的零，保证 100.00 与 100.0 视为相同金额
     */
    private static String normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.stripTrailingZeros().toPlainString();
    }
}
