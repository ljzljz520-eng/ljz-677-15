package com.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.util.ListUtils;
import com.excel.dto.ExcelDataDTO;
import com.excel.entity.ImportStaging;
import com.excel.mapper.ImportStagingMapper;
import com.excel.utils.DuplicateKeyUtils;
import com.excel.utils.ValidationUtils;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 预检阶段的EasyExcel监听器
 * 1. 逐行校验（不通过的行valid_flag=0并记录错误信息，仍落暂存表供前端展示）
 * 2. 计算判重键（医保编号+就诊日期+项目编码+金额），标记文件内疑似重复（重复组中第2行起）
 * 3. 批量写入 import_staging，避免OOM
 * 历史重复由Service在解析完成后统一回查标记。
 */
public class StagingDataListener implements ReadListener<ExcelDataDTO> {

    private static final Logger logger = LoggerFactory.getLogger(StagingDataListener.class);

    private static final int BATCH_COUNT = 1000;

    private final ImportStagingMapper stagingMapper;
    private final String checkNo;
    private final String fileName;
    private final Long operatorId;

    private List<ImportStaging> cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);

    /** 已出现过的判重键：用于标记文件内重复 */
    private final Set<String> seenKeys = new HashSet<>();

    @Getter
    private int totalCount = 0;
    @Getter
    private int invalidCount = 0;
    @Getter
    private int duplicateInFileCount = 0;

    /** 有效且不重复的行所使用过的判重键（供历史查重） */
    @Getter
    private final Set<String> validDedupKeys = new HashSet<>();

    public StagingDataListener(ImportStagingMapper stagingMapper, String checkNo,
                               String fileName, Long operatorId) {
        this.stagingMapper = stagingMapper;
        this.checkNo = checkNo;
        this.fileName = fileName;
        this.operatorId = operatorId;
    }

    @Override
    public void invoke(ExcelDataDTO data, AnalysisContext context) {
        totalCount++;
        Integer rowIndex = context.readRowHolder().getRowIndex() + 1;
        data.setRowIndex(rowIndex);

        ImportStaging staging = new ImportStaging();
        staging.setCheckNo(checkNo);
        staging.setFileName(fileName);
        staging.setRowIndex(rowIndex);
        staging.setDataCode(trim(data.getDataCode()));
        staging.setName(trim(data.getName()));
        staging.setIdCard(trim(data.getIdCard()));
        staging.setPhone(trim(data.getPhone()));
        staging.setMedicalInsuranceNo(trim(data.getMedicalInsuranceNo()));
        staging.setVisitDate(data.getVisitDate());
        staging.setItemCode(trim(data.getItemCode()));
        staging.setAmount(data.getAmount());
        staging.setAddress(data.getAddress());
        staging.setRemark(data.getRemark());
        staging.setOperatorId(operatorId);
        staging.setDuplicateInFile(0);
        staging.setDuplicateInHistory(0);
        staging.setImportedFlag(0);

        // 后端校验
        String errorMsg = ValidationUtils.validate(data);
        String dedupKey = DuplicateKeyUtils.buildKey(
                data.getMedicalInsuranceNo(), data.getVisitDate(),
                data.getItemCode(), data.getAmount());
        staging.setDedupKey(dedupKey);

        if (errorMsg != null) {
            staging.setValidFlag(0);
            staging.setErrorMsg(errorMsg);
            invalidCount++;
        } else {
            staging.setValidFlag(1);
            // 文件内重复：同一判重键在本文件再次出现即疑似重复
            if (dedupKey != null) {
                if (!seenKeys.add(dedupKey)) {
                    staging.setDuplicateInFile(1);
                    duplicateInFileCount++;
                }
                validDedupKeys.add(dedupKey);
            }
        }

        cachedDataList.add(staging);
        if (cachedDataList.size() >= BATCH_COUNT) {
            saveData();
            cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        if (!cachedDataList.isEmpty()) {
            saveData();
        }
        logger.info("预检解析完成 checkNo={}，总计{}行，无效{}行，文件内疑似重复{}行",
                checkNo, totalCount, invalidCount, duplicateInFileCount);
    }

    private void saveData() {
        for (ImportStaging staging : cachedDataList) {
            stagingMapper.insert(staging);
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
