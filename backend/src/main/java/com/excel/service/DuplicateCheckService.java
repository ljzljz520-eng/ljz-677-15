package com.excel.service;

import com.excel.dto.DuplicateItemDTO;
import com.excel.entity.ExcelData;
import com.excel.mapper.ExcelDataMapper;
import com.excel.utils.DuplicateKeyUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 重复数据检测服务
 * 重复判断维度：医保编号 + 就诊日期 + 项目编码 + 金额
 * 检测两类重复：
 * 1. 文件内疑似重复：同一文件内相同键出现多次
 * 2. 历史批次已上送：相同键的记录在历史批次中已成功上送（report_status=1）
 */
@Service
@RequiredArgsConstructor
public class DuplicateCheckService {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateCheckService.class);

    /**
     * 重复类型：正常
     */
    public static final int DUPLICATE_TYPE_NONE = 0;

    /**
     * 重复类型：文件内疑似重复
     */
    public static final int DUPLICATE_TYPE_IN_FILE = 1;

    /**
     * 重复类型：历史批次已上送
     */
    public static final int DUPLICATE_TYPE_REPORTED = 2;

    private final ExcelDataMapper excelDataMapper;

    /**
     * 对预检数据执行重复检测，就地标记 duplicateType
     *
     * @param validList 校验通过的数据
     * @return 重复检测结果（文件内重复明细 + 历史已上送明细）
     */
    public DuplicateCheckResult check(List<ExcelData> validList) {
        // 1. 加载历史已成功上送记录的重复键 -> 批次号
        Map<String, String> reportedKeyMap = loadReportedKeyMap();

        // 2. 遍历预检数据，逐条判定
        Set<String> seenInFile = new HashSet<>();
        List<DuplicateItemDTO> fileDuplicateList = new ArrayList<>();
        List<DuplicateItemDTO> historyDuplicateList = new ArrayList<>();

        int rowIndex = 0;
        for (ExcelData data : validList) {
            rowIndex++;
            String key = DuplicateKeyUtils.buildKey(
                    data.getMedicalInsuranceNo(), data.getVisitDate(),
                    data.getItemCode(), data.getAmount());

            // 优先判定历史已上送（更严重，避免重复上送）
            if (reportedKeyMap.containsKey(key)) {
                data.setDuplicateType(DUPLICATE_TYPE_REPORTED);
                String relatedBatchNo = reportedKeyMap.get(key);
                historyDuplicateList.add(buildItem(data, rowIndex, DUPLICATE_TYPE_REPORTED,
                        "与历史批次已成功上送的记录重复", relatedBatchNo));
                continue;
            }

            // 其次判定文件内重复：同键第二次及以后出现视为疑似重复
            if (!seenInFile.add(key)) {
                data.setDuplicateType(DUPLICATE_TYPE_IN_FILE);
                fileDuplicateList.add(buildItem(data, rowIndex, DUPLICATE_TYPE_IN_FILE,
                        "与本文件内其他记录的医保编号、就诊日期、项目编码、金额完全相同", null));
            }
        }

        logger.info("重复检测完成：校验通过{}条，文件内疑似重复{}条，历史批次已上送{}条",
                validList.size(), fileDuplicateList.size(), historyDuplicateList.size());

        return new DuplicateCheckResult(fileDuplicateList, historyDuplicateList);
    }

    /**
     * 加载历史已成功上送记录的重复键
     * key: 重复判断键, value: 首次成功上送的批次号
     */
    private Map<String, String> loadReportedKeyMap() {
        List<ExcelData> reportedList = excelDataMapper.selectReportedRecords();
        Map<String, String> reportedKeyMap = new HashMap<>(reportedList.size() * 2);
        for (ExcelData data : reportedList) {
            String key = DuplicateKeyUtils.buildKey(
                    data.getMedicalInsuranceNo(), data.getVisitDate(),
                    data.getItemCode(), data.getAmount());
            reportedKeyMap.putIfAbsent(key, data.getBatchNo());
        }
        return reportedKeyMap;
    }

    private DuplicateItemDTO buildItem(ExcelData data, int rowIndex, int duplicateType,
                                       String remark, String relatedBatchNo) {
        return DuplicateItemDTO.builder()
                .rowIndex(rowIndex)
                .dataCode(data.getDataCode())
                .name(data.getName())
                .medicalInsuranceNo(data.getMedicalInsuranceNo())
                .visitDate(data.getVisitDate())
                .itemCode(data.getItemCode())
                .amount(data.getAmount())
                .duplicateType(duplicateType)
                .duplicateRemark(remark)
                .relatedBatchNo(relatedBatchNo)
                .build();
    }

    /**
     * 重复检测结果
     */
    public record DuplicateCheckResult(List<DuplicateItemDTO> fileDuplicateList,
                                       List<DuplicateItemDTO> historyDuplicateList) {
    }
}
