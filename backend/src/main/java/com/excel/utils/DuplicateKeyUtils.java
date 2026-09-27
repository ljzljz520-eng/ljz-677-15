package com.excel.utils;

import cn.hutool.core.util.StrUtil;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 重复判断工具
 * 判重四要素：医保编号 + 就诊日期 + 项目编码 + 金额
 * 前端不做任何判重计算，所有键的规范化均在后端完成
 */
public final class DuplicateKeyUtils {

    private DuplicateKeyUtils() {
    }

    /**
     * 构建规范化的判重键。任何要素缺失返回null（不参与判重，通常该行校验也不会通过）
     */
    public static String buildKey(String medicalInsuranceNo, LocalDate visitDate,
                                  String itemCode, BigDecimal amount) {
        if (StrUtil.isBlank(medicalInsuranceNo) || visitDate == null
                || StrUtil.isBlank(itemCode) || amount == null) {
            return null;
        }
        return normalizeInsuranceNo(medicalInsuranceNo)
                + "|" + visitDate.toString()
                + "|" + normalizeItemCode(itemCode)
                + "|" + normalizeAmount(amount);
    }

    /**
     * 医保编号：去空白后转大写，消除大小写/空格差异
     */
    public static String normalizeInsuranceNo(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", "").toUpperCase();
    }

    /**
     * 项目编码：去空白后转大写
     */
    public static String normalizeItemCode(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", "").toUpperCase();
    }

    /**
     * 金额：按两位小数规范化（DECIMAL(15,2)），100 与 100.00 视为相同
     */
    public static String normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
