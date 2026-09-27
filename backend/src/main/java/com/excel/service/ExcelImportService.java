package com.excel.service;

import cn.hutool.core.util.IdUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.excel.dto.ExcelDataDTO;
import com.excel.dto.ImportResultDTO;
import com.excel.dto.PrecheckResultDTO;
import com.excel.entity.ExcelData;
import com.excel.entity.ImportRecord;
import com.excel.entity.User;
import com.excel.listener.PrecheckDataListener;
import com.excel.mapper.ExcelDataMapper;
import com.excel.mapper.ImportRecordMapper;
import com.excel.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelImportService.class);

    /**
     * 重复数据处理策略：排除疑似重复数据
     */
    public static final String STRATEGY_EXCLUDE = "EXCLUDE";

    /**
     * 重复数据处理策略：全部导入
     */
    public static final String STRATEGY_INCLUDE = "INCLUDE";

    private final ExcelDataMapper excelDataMapper;
    private final ImportRecordMapper importRecordMapper;
    private final UserMapper userMapper;
    private final DuplicateCheckService duplicateCheckService;
    private final PrecheckCache precheckCache;

    /**
     * 导入预检：解析文件、校验数据、检测重复，结果暂存等待用户确认
     * 重复判断（文件内疑似重复 + 历史批次已上送）全部在后端完成
     */
    public PrecheckResultDTO precheck(MultipartFile file, Long operatorId) throws IOException {
        String checkNo = IdUtil.fastSimpleUUID();
        String fileName = file.getOriginalFilename();

        logger.info("开始导入预检: 文件={}, 预检编号={}", fileName, checkNo);

        // 1. 解析并校验（不写库）
        PrecheckDataListener listener = new PrecheckDataListener();
        ExcelTypeEnum excelType = fileName != null && fileName.endsWith(".xlsx")
                ? ExcelTypeEnum.XLSX : ExcelTypeEnum.XLS;

        try {
            EasyExcel.read(file.getInputStream(), ExcelDataDTO.class, listener)
                    .excelType(excelType)
                    .charset(StandardCharsets.UTF_8)
                    .sheet()
                    .headRowNumber(1)
                    .doRead();
        } catch (Exception e) {
            logger.error("Excel预检解析失败", e);
            throw new RuntimeException("Excel解析失败: " + e.getMessage(), e);
        }

        List<ExcelData> validList = listener.getValidList();

        // 2. 重复检测（后端判断：文件内疑似重复 + 历史批次已上送）
        DuplicateCheckService.DuplicateCheckResult checkResult = duplicateCheckService.check(validList);

        // 3. 暂存预检数据，等待用户确认
        precheckCache.put(checkNo, validList, listener.getErrorList(),
                listener.getTotalCount(), fileName);

        int fileDuplicateCount = checkResult.fileDuplicateList().size();
        int historyDuplicateCount = checkResult.historyDuplicateList().size();
        int normalCount = validList.size() - fileDuplicateCount - historyDuplicateCount;
        boolean hasDuplicates = fileDuplicateCount > 0 || historyDuplicateCount > 0;

        String message;
        if (hasDuplicates) {
            message = String.format("预检发现疑似重复数据：文件内重复%d条，历史批次已上送%d条，请确认后导入",
                    fileDuplicateCount, historyDuplicateCount);
        } else {
            message = "预检通过，未发现重复数据，请确认导入";
        }

        logger.info("导入预检完成: checkNo={}, 总计{}条, 正常{}条, 文件内重复{}条, 历史已上送{}条, 校验失败{}条",
                checkNo, listener.getTotalCount(), normalCount,
                fileDuplicateCount, historyDuplicateCount, listener.getErrorList().size());

        return PrecheckResultDTO.builder()
                .checkNo(checkNo)
                .fileName(fileName)
                .totalCount(listener.getTotalCount())
                .errorCount(listener.getErrorList().size())
                .normalCount(normalCount)
                .fileDuplicateCount(fileDuplicateCount)
                .historyDuplicateCount(historyDuplicateCount)
                .fileDuplicateList(checkResult.fileDuplicateList())
                .historyDuplicateList(checkResult.historyDuplicateList())
                .errorList(listener.getErrorList())
                .hasDuplicates(hasDuplicates)
                .message(message)
                .build();
    }

    /**
     * 确认导入：用户确认预检结果后，将暂存数据正式入库
     *
     * @param checkNo           预检编号
     * @param duplicateStrategy 重复数据处理策略：EXCLUDE-排除重复 INCLUDE-全部导入
     * @param operatorId        操作人ID
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResultDTO confirmImport(String checkNo, String duplicateStrategy, Long operatorId) {
        PrecheckCache.CacheEntry entry = precheckCache.get(checkNo);
        if (entry == null) {
            throw new RuntimeException("预检数据不存在或已过期，请重新上传文件进行预检");
        }

        String batchNo = IdUtil.fastSimpleUUID();
        logger.info("确认导入: checkNo={}, 批次号={}, 重复策略={}", checkNo, batchNo, duplicateStrategy);

        // 获取操作人信息
        User operator = userMapper.selectById(operatorId);
        String operatorName = operator != null ? operator.getRealName() : "系统";

        // 按策略过滤数据
        boolean includeDuplicates = STRATEGY_INCLUDE.equalsIgnoreCase(duplicateStrategy);
        List<ExcelData> toImport = new ArrayList<>();
        int excludedDuplicateCount = 0;
        for (ExcelData data : entry.getValidList()) {
            boolean isDuplicate = data.getDuplicateType() != null
                    && data.getDuplicateType() != DuplicateCheckService.DUPLICATE_TYPE_NONE;
            if (isDuplicate && !includeDuplicates) {
                excludedDuplicateCount++;
                continue;
            }
            data.setBatchNo(batchNo);
            toImport.add(data);
        }

        // 创建导入记录
        ImportRecord record = new ImportRecord();
        record.setBatchNo(batchNo);
        record.setFileName(entry.getFileName());
        record.setFileSize(0L);
        record.setStatus(0);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        importRecordMapper.insert(record);

        // 批量入库
        int successCount = 0;
        int failCount = 0;
        List<ExcelDataDTO> saveErrorList = new ArrayList<>(entry.getErrorList());
        for (ExcelData data : toImport) {
            try {
                excelDataMapper.insert(data);
                successCount++;
            } catch (Exception e) {
                failCount++;
                ExcelDataDTO errorDto = new ExcelDataDTO();
                errorDto.setDataCode(data.getDataCode());
                errorDto.setName(data.getName());
                errorDto.setErrorMsg("数据库保存失败: " + e.getMessage());
                saveErrorList.add(errorDto);
                logger.error("数据保存失败: dataCode={}", data.getDataCode(), e);
            }
        }

        int totalFailCount = failCount + entry.getErrorList().size();

        // 更新导入记录
        record.setTotalCount(entry.getTotalCount());
        record.setSuccessCount(successCount);
        record.setFailCount(totalFailCount);
        record.setStatus(totalFailCount > 0 ? 2 : 1);
        if (!saveErrorList.isEmpty()) {
            StringBuilder errorDetails = new StringBuilder();
            for (ExcelDataDTO error : saveErrorList) {
                errorDetails.append("第").append(error.getRowIndex() != null ? error.getRowIndex() : "?")
                        .append("行: ").append(error.getErrorMsg()).append("\n");
            }
            record.setErrorDetails(errorDetails.toString());
        }
        importRecordMapper.updateById(record);

        // 导入完成，清除暂存
        precheckCache.remove(checkNo);

        logger.info("确认导入完成: 批次号={}, 导入{}条, 排除重复{}条, 失败{}条",
                batchNo, successCount, excludedDuplicateCount, totalFailCount);

        return ImportResultDTO.builder()
                .batchNo(batchNo)
                .totalCount(entry.getTotalCount())
                .successCount(successCount)
                .failCount(totalFailCount)
                .excludedDuplicateCount(excludedDuplicateCount)
                .errorList(saveErrorList)
                .status(totalFailCount > 0 ? "completed_with_errors" : "completed")
                .message(String.format("导入完成，成功%d条，排除疑似重复%d条，失败%d条",
                        successCount, excludedDuplicateCount, totalFailCount))
                .build();
    }

    /**
     * 获取导入记录列表
     */
    public Page<ImportRecord> getImportRecords(Integer pageNum, Integer pageSize) {
        Page<ImportRecord> page = new Page<>(pageNum, pageSize);
        return importRecordMapper.selectPage(page,
                new LambdaQueryWrapper<ImportRecord>()
                        .orderByDesc(ImportRecord::getCreateTime));
    }

    /**
     * 根据批次号获取数据
     */
    public Page<ExcelData> getDataByBatch(String batchNo, Integer pageNum, Integer pageSize) {
        Page<ExcelData> page = new Page<>(pageNum, pageSize);
        return excelDataMapper.selectPage(page,
                new LambdaQueryWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batchNo)
                        .orderByAsc(ExcelData::getId));
    }

    /**
     * 获取待上报数据
     */
    public List<ExcelData> getPendingReportData(String batchNo) {
        return excelDataMapper.selectByBatchAndStatus(batchNo, 0);
    }

    /**
     * 下载导入模板
     */
    public byte[] downloadTemplate() {
        // 返回模板的字节数组
        return null; // Controller中处理
    }
}
