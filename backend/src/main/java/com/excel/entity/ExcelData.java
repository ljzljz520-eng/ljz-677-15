package com.excel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("excel_data")
public class ExcelData {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 数据编号
     */
    private String dataCode;

    /**
     * 名称
     */
    private String name;

    /**
     * 身份证号
     */
    private String idCard;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 医保编号（参保人医保编号）
     */
    private String medicalInsuranceNo;

    /**
     * 就诊日期
     */
    private LocalDate visitDate;

    /**
     * 项目编码（诊疗/收费项目编码）
     */
    private String itemCode;

    /**
     * 金额
     */
    private BigDecimal amount;

    /**
     * 地址
     */
    private String address;

    /**
     * 备注
     */
    private String remark;

    /**
     * 判重键：规范化的医保编号|就诊日期|项目编码|金额
     */
    private String dedupKey;

    /**
     * 上传批次号
     */
    private String batchNo;

    /**
     * 上报状态：0-待上报 1-已上报 2-上报失败
     */
    private Integer reportStatus;

    /**
     * 上报结果信息
     */
    private String reportMessage;

    /**
     * 上报时间
     */
    private LocalDateTime reportTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
