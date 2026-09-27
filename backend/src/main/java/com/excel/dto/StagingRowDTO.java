package com.excel.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 暂存区一行数据（含后端判定的重复标记），供前端展示
 */
@Data
public class StagingRowDTO {

    private Long id;

    private Integer rowIndex;

    private String dataCode;

    private String name;

    private String idCard;

    private String phone;

    private String medicalInsuranceNo;

    private LocalDate visitDate;

    private String itemCode;

    private BigDecimal amount;

    private String address;

    private String remark;

    private Integer validFlag;

    private String errorMsg;

    /** 文件内疑似重复：0-否 1-是 */
    private Integer duplicateInFile;

    /** 历史已上送重复：0-否 1-是 */
    private Integer duplicateInHistory;

    private Long refHistoryId;

    private String refHistoryBatch;

    private LocalDateTime refHistoryTime;

    private Integer importedFlag;

    /**
     * 前端展示用的重复类型归类：
     * invalid-校验失败 / file-文件内重复 / history-历史已上送 / both-两者都是 / clean-无问题
     */
    private String duplicateType;

    /**
     * 前端展示用的重复说明（后端生成）
     */
    private String duplicateTip;
}
