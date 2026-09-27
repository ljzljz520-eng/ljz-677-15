package com.excel.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 文件预检结果（重复清单摘要）
 * 重复判断全部在后端完成，前端只据此展示
 */
@Data
@Builder
public class PreCheckResultDTO {

    /**
     * 预检批次号，后续查看明细/确认导入/取消都使用它
     */
    private String checkNo;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件总行数（不含表头）
     */
    private Integer totalCount;

    /**
     * 校验失败行数
     */
    private Integer invalidCount;

    /**
     * 有效行数
     */
    private Integer validCount;

    /**
     * 文件内疑似重复行数（同文件内 医保编号+就诊日期+项目编码+金额 相同）
     */
    private Integer duplicateInFileCount;

    /**
     * 命中历史已成功上送的行数
     */
    private Integer duplicateInHistoryCount;

    /**
     * 涉及任一重复类型的行数（去重）
     */
    private Integer duplicateCount;

    /**
     * 无任何问题、可直接导入的行数
     */
    private Integer cleanCount;

    /**
     * 是否存在需要用户确认的疑似重复
     */
    private Boolean hasDuplicate;

    private String message;
}
