package com.excel.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 疑似重复数据明细
 */
@Data
@Builder
public class DuplicateItemDTO {

    /**
     * Excel行号
     */
    private Integer rowIndex;

    /**
     * 数据编号
     */
    private String dataCode;

    /**
     * 姓名
     */
    private String name;

    /**
     * 医保编号
     */
    private String medicalInsuranceNo;

    /**
     * 就诊日期
     */
    private String visitDate;

    /**
     * 项目编码
     */
    private String itemCode;

    /**
     * 金额
     */
    private BigDecimal amount;

    /**
     * 重复类型：1-文件内疑似重复 2-历史批次已上送
     */
    private Integer duplicateType;

    /**
     * 重复说明
     */
    private String duplicateRemark;

    /**
     * 历史已上送记录所在的批次号（duplicateType=2时有值）
     */
    private String relatedBatchNo;
}
