package com.excel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 导入暂存数据
 * 文件预检阶段先落到此表，用户对疑似重复记录确认后才转入 excel_data 正式表
 */
@Data
@TableName("import_staging")
public class ImportStaging {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 预检批次号（一次文件预检唯一）
     */
    private String checkNo;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * Excel行号（含表头，数据行从2开始）
     */
    private Integer rowIndex;

    private String dataCode;

    private String name;

    private String idCard;

    private String phone;

    /**
     * 医保编号
     */
    private String medicalInsuranceNo;

    /**
     * 就诊日期
     */
    private LocalDate visitDate;

    /**
     * 项目编码
     */
    private String itemCode;

    private BigDecimal amount;

    private String address;

    private String remark;

    /**
     * 判重键：规范化的医保编号|就诊日期|项目编码|金额
     */
    private String dedupKey;

    /**
     * 是否通过校验：0-无效 1-有效
     */
    private Integer validFlag;

    /**
     * 校验错误信息
     */
    private String errorMsg;

    /**
     * 文件内疑似重复：0-否 1-是
     */
    private Integer duplicateInFile;

    /**
     * 历史已上送重复：0-否 1-是
     */
    private Integer duplicateInHistory;

    /**
     * 命中的历史已上送数据ID
     */
    private Long refHistoryId;

    /**
     * 命中的历史已上送数据批次号
     */
    private String refHistoryBatch;

    /**
     * 命中的历史记录上送时间
     */
    private LocalDateTime refHistoryTime;

    /**
     * 是否已转入正式表：0-否 1-是
     */
    private Integer importedFlag;

    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
