package com.excel.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户确认导入请求
 * 无重复记录由后端自动导入；疑似重复记录必须用户显式勾选确认后才导入
 */
@Data
public class ConfirmImportRequest {

    /**
     * 用户确认要导入的疑似重复暂存行ID集合（文件内重复 + 历史重复）
     * 为空表示一条疑似重复都不确认导入
     */
    private List<Long> confirmedDuplicateIds;
}
