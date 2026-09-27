package com.excel.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 导入预检结果
 * 上传文件后先进行预检，返回重复检测结果，用户确认后才正式导入
 */
@Data
@Builder
public class PrecheckResultDTO {

    /**
     * 预检编号，确认导入时凭此编号取回暂存数据
     */
    private String checkNo;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 总记录数
     */
    private Integer totalCount;

    /**
     * 校验失败数量
     */
    private Integer errorCount;

    /**
     * 正常数据数量（可安全导入）
     */
    private Integer normalCount;

    /**
     * 文件内疑似重复数量
     */
    private Integer fileDuplicateCount;

    /**
     * 历史批次已上送数量
     */
    private Integer historyDuplicateCount;

    /**
     * 文件内疑似重复明细
     */
    private List<DuplicateItemDTO> fileDuplicateList;

    /**
     * 历史批次已上送明细
     */
    private List<DuplicateItemDTO> historyDuplicateList;

    /**
     * 校验失败明细
     */
    private List<ExcelDataDTO> errorList;

    /**
     * 是否存在疑似重复数据（需要用户确认后才能导入）
     */
    private Boolean hasDuplicates;

    /**
     * 提示消息
     */
    private String message;
}
