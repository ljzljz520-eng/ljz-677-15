package com.excel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 确认导入请求
 */
@Data
public class ConfirmImportRequest {

    /**
     * 预检编号
     */
    @NotBlank(message = "预检编号不能为空")
    private String checkNo;

    /**
     * 重复数据处理策略：
     * EXCLUDE-排除疑似重复数据，仅导入正常数据；
     * INCLUDE-全部导入（含疑似重复及历史已上送数据）
     */
    @NotBlank(message = "重复数据处理策略不能为空")
    private String duplicateStrategy;
}
