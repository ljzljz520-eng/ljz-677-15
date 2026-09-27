package com.excel.listener;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.excel.dto.ExcelDataDTO;
import com.excel.entity.ExcelData;
import com.excel.utils.DuplicateKeyUtils;
import com.excel.utils.ValidationUtils;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 预检数据监听器
 * 仅解析和校验数据并收集到内存，不写库；
 * 重复检测与入库在用户确认后执行
 */
public class PrecheckDataListener implements ReadListener<ExcelDataDTO> {

    private static final Logger logger = LoggerFactory.getLogger(PrecheckDataListener.class);

    /**
     * 校验通过的数据（暂存，等待重复检测与用户确认）
     */
    @Getter
    private final List<ExcelData> validList = new ArrayList<>();

    /**
     * 校验失败的数据
     */
    @Getter
    private final List<ExcelDataDTO> errorList = new ArrayList<>();

    /**
     * 总条数
     */
    @Getter
    private int totalCount = 0;

    @Override
    public void invoke(ExcelDataDTO data, AnalysisContext context) {
        totalCount++;
        Integer rowIndex = context.readRowHolder().getRowIndex() + 1;
        data.setRowIndex(rowIndex);

        // 数据校验
        String errorMsg = ValidationUtils.validate(data);
        if (errorMsg != null) {
            data.setErrorMsg(errorMsg);
            errorList.add(data);
            logger.warn("第{}行数据校验失败: {}", rowIndex, errorMsg);
            return;
        }

        // 转换为实体，就诊日期统一归一化为 yyyy-MM-dd，保证后续重复判断准确
        ExcelData entity = new ExcelData();
        BeanUtil.copyProperties(data, entity);
        entity.setVisitDate(DuplicateKeyUtils.normalizeVisitDate(data.getVisitDate()));
        entity.setReportStatus(0);
        entity.setDuplicateType(0);

        validList.add(entity);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        logger.info("Excel预检解析完成！总计：{}条，校验通过：{}条，校验失败：{}条",
                totalCount, validList.size(), errorList.size());
    }
}
