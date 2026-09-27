package com.excel.service;

import cn.hutool.core.util.IdUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.excel.dto.ExcelDataDTO;
import com.excel.dto.ImportResultDTO;
import com.excel.dto.PreCheckResultDTO;
import com.excel.dto.StagingRowDTO;
import com.excel.entity.ExcelData;
import com.excel.entity.ImportRecord;
import com.excel.entity.ImportStaging;
import com.excel.entity.User;
import com.excel.listener.StagingDataListener;
import com.excel.mapper.ExcelDataMapper;
import com.excel.mapper.ImportRecordMapper;
import com.excel.mapper.ImportStagingMapper;
import com.excel.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelImportService.class);

    /** 历史查重时，IN 查询判重键的分批大小 */
    private static final int KEY_QUERY_CHUNK = 500;

    private final ExcelDataMapper excelDataMapper;
    private final ImportRecordMapper importRecordMapper;
    private final ImportStagingMapper importStagingMapper;
    private final UserMapper userMapper;

    // ============================= 预检：解析 + 重复判断（全部在后端） =============================

    /**
     * 文件预检：解析Excel -> 逐行校验 -> 标记文件内疑似重复 -> 回查历史已成功上送记录。
     * 数据仅写入暂存表，不进入正式表，等待用户确认。
     */
    @Transactional(rollbackFor = Exception.class)
    public PreCheckResultDTO preCheck(MultipartFile file, Long operatorId) throws IOException {
        String checkNo = IdUtil.fastSimpleUUID();
        String fileName = file.getOriginalFilename();
        logger.info("开始导入预检: {}, 大小={}bytes, checkNo={}", fileName, file.getSize(), checkNo);

        StagingDataListener listener =
                new StagingDataListener(importStagingMapper, checkNo, fileName, operatorId);

        ExcelTypeEnum excelType = fileName != null && fileName.endsWith(".xlsx")
                ? ExcelTypeEnum.XLSX : ExcelTypeEnum.XLS;

        EasyExcel.read(file.getInputStream(), ExcelDataDTO.class, listener)
                .excelType(excelType)
                .charset(StandardCharsets.UTF_8)
                .sheet()
                .headRowNumber(1)
                .doRead();

        // 历史重复回查（仅比对历史中已成功上送 report_status=1 的记录）
        int duplicateInHistoryCount = markHistoryDuplicates(checkNo, listener.getValidDedupKeys());

        int total = listener.getTotalCount();
        int invalid = listener.getInvalidCount();
        int dupInFile = listener.getDuplicateInFileCount();
        int valid = total - invalid;

        // 涉及任一重复类型的行数（去重）与干净行数，由数据库统计保证准确
        long dupAny = countStaging(checkNo, null, true, false);
        long clean = countStaging(checkNo, "clean", false, false);

        boolean hasDuplicate = dupAny > 0;
        String message;
        if (total == 0) {
            message = "文件中没有数据行";
        } else if (hasDuplicate) {
            message = String.format(
                    "检测到疑似重复：文件内重复%d条、与历史已上送重复%d条，请在下方清单中确认后再导入",
                    dupInFile, duplicateInHistoryCount);
        } else {
            message = String.format("预检通过，共%d条有效数据，未发现重复，可直接导入", valid);
        }

        return PreCheckResultDTO.builder()
                .checkNo(checkNo)
                .fileName(fileName)
                .totalCount(total)
                .invalidCount(invalid)
                .validCount(valid)
                .duplicateInFileCount(dupInFile)
                .duplicateInHistoryCount(duplicateInHistoryCount)
                .duplicateCount((int) dupAny)
                .cleanCount((int) clean)
                .hasDuplicate(hasDuplicate)
                .message(message)
                .build();
    }

    /**
     * 回查历史已成功上送数据，标记暂存表中的历史重复行。
     *
     * @return 命中历史重复的暂存行数
     */
    private int markHistoryDuplicates(String checkNo, Set<String> dedupKeys) {
        if (dedupKeys == null || dedupKeys.isEmpty()) {
            return 0;
        }

        List<String> keyList = new ArrayList<>(dedupKeys);
        Map<String, Map<String, Object>> latestByKey = new HashMap<>();

        for (int i = 0; i < keyList.size(); i += KEY_QUERY_CHUNK) {
            List<String> chunk = keyList.subList(i, Math.min(i + KEY_QUERY_CHUNK, keyList.size()));
            List<Map<String, Object>> rows =
                    importStagingMapper.selectLatestReportedByKeys(chunk);
            for (Map<String, Object> row : rows) {
                latestByKey.put(String.valueOf(getCol(row, "dedupKey")), row);
            }
        }

        if (latestByKey.isEmpty()) {
            return 0;
        }

        // 取出命中键对应的暂存行ID，按判重键分组后批量标记（命中键可能很多，分批查询）
        List<String> hitKeys = new ArrayList<>(latestByKey.keySet());
        Map<String, List<Long>> idsByKey = new HashMap<>();
        for (int i = 0; i < hitKeys.size(); i += KEY_QUERY_CHUNK) {
            List<String> chunk = hitKeys.subList(i, Math.min(i + KEY_QUERY_CHUNK, hitKeys.size()));
            List<ImportStaging> hitStaging = importStagingMapper.selectList(
                    new LambdaQueryWrapper<ImportStaging>()
                            .eq(ImportStaging::getCheckNo, checkNo)
                            .eq(ImportStaging::getValidFlag, 1)
                            .in(ImportStaging::getDedupKey, chunk)
                            .select(ImportStaging::getId, ImportStaging::getDedupKey));
            for (ImportStaging s : hitStaging) {
                idsByKey.computeIfAbsent(s.getDedupKey(), k -> new ArrayList<>()).add(s.getId());
            }
        }

        int marked = 0;
        for (Map.Entry<String, List<Long>> entry : idsByKey.entrySet()) {
            Map<String, Object> ref = latestByKey.get(entry.getKey());
            if (ref == null) {
                continue;
            }
            Long historyId = ((Number) getCol(ref, "id")).longValue();
            Object batchVal = getCol(ref, "batchNo");
            String batchNo = batchVal == null ? null : String.valueOf(batchVal);
            LocalDateTime reportTime = toLocalDateTime(getCol(ref, "reportTime"));

            // 单个判重键下的暂存行也分批，避免IN参数过大
            List<Long> ids = entry.getValue();
            for (int i = 0; i < ids.size(); i += KEY_QUERY_CHUNK) {
                List<Long> idChunk = ids.subList(i, Math.min(i + KEY_QUERY_CHUNK, ids.size()));
                marked += importStagingMapper.markHistoryDuplicate(
                        idChunk, historyId, batchNo, reportTime);
            }
        }
        logger.info("历史重复标记完成 checkNo={}，命中{}个暂存行", checkNo, marked);
        return marked;
    }

    /**
     * 大小写不敏感读取Map列（MySQL保留别名大小写，部分库/驱动返回小写）
     */
    private Object getCol(Map<String, Object> row, String column) {
        Object direct = row.get(column);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(column)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * 兼容不同JDBC驱动/MyBatis对DATETIME列返回的时间类型
     */
    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        }
        return null;
    }

    /**
     * 统计暂存行数量。
     *
     * @param category   invalid/file/history/clean/null(全部)
     * @param dupOnly    true=仅统计涉及重复的行（category 传null时生效）
     */
    private long countStaging(String checkNo, String category, boolean dupOnly, boolean ignore) {
        LambdaQueryWrapper<ImportStaging> wrapper =
                new LambdaQueryWrapper<ImportStaging>().eq(ImportStaging::getCheckNo, checkNo);
        applyCategory(wrapper, category, dupOnly);
        return importStagingMapper.selectCount(wrapper);
    }

    /**
     * 分页查看暂存清单（重复判断结果由后端给出，前端只展示）。
     */
    public Page<StagingRowDTO> getStagingPage(String checkNo, String category,
                                              Integer pageNum, Integer pageSize) {
        Page<ImportStaging> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ImportStaging> wrapper = new LambdaQueryWrapper<ImportStaging>()
                .eq(ImportStaging::getCheckNo, checkNo)
                .orderByAsc(ImportStaging::getRowIndex);
        applyCategory(wrapper, category, false);

        Page<ImportStaging> result = importStagingMapper.selectPage(page, wrapper);

        Page<StagingRowDTO> dtoPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        dtoPage.setRecords(result.getRecords().stream()
                .map(this::toStagingRowDTO)
                .collect(Collectors.toList()));
        return dtoPage;
    }

    /**
     * 按类别拼查询条件。类别优先级：invalid > file(含both) > history(含both) > clean
     */
    private void applyCategory(LambdaQueryWrapper<ImportStaging> wrapper,
                               String category, boolean dupOnly) {
        if (category == null) {
            if (dupOnly) {
                wrapper.and(w -> w.eq(ImportStaging::getDuplicateInFile, 1)
                        .or().eq(ImportStaging::getDuplicateInHistory, 1));
            }
            return;
        }
        switch (category) {
            case "invalid" -> wrapper.eq(ImportStaging::getValidFlag, 0);
            case "file" -> wrapper.eq(ImportStaging::getValidFlag, 1)
                    .eq(ImportStaging::getDuplicateInFile, 1);
            case "history" -> wrapper.eq(ImportStaging::getValidFlag, 1)
                    .eq(ImportStaging::getDuplicateInHistory, 1);
            case "clean" -> wrapper.eq(ImportStaging::getValidFlag, 1)
                    .eq(ImportStaging::getDuplicateInFile, 0)
                    .eq(ImportStaging::getDuplicateInHistory, 0);
            default -> {
                // 未知类别不加额外条件
            }
        }
    }

    private StagingRowDTO toStagingRowDTO(ImportStaging s) {
        StagingRowDTO dto = new StagingRowDTO();
        dto.setId(s.getId());
        dto.setRowIndex(s.getRowIndex());
        dto.setDataCode(s.getDataCode());
        dto.setName(s.getName());
        dto.setIdCard(s.getIdCard());
        dto.setPhone(s.getPhone());
        dto.setMedicalInsuranceNo(s.getMedicalInsuranceNo());
        dto.setVisitDate(s.getVisitDate());
        dto.setItemCode(s.getItemCode());
        dto.setAmount(s.getAmount());
        dto.setAddress(s.getAddress());
        dto.setRemark(s.getRemark());
        dto.setValidFlag(s.getValidFlag());
        dto.setErrorMsg(s.getErrorMsg());
        dto.setDuplicateInFile(s.getDuplicateInFile());
        dto.setDuplicateInHistory(s.getDuplicateInHistory());
        dto.setRefHistoryId(s.getRefHistoryId());
        dto.setRefHistoryBatch(s.getRefHistoryBatch());
        dto.setRefHistoryTime(s.getRefHistoryTime());
        dto.setImportedFlag(s.getImportedFlag());

        boolean invalid = s.getValidFlag() != null && s.getValidFlag() == 0;
        boolean dupFile = s.getDuplicateInFile() != null && s.getDuplicateInFile() == 1;
        boolean dupHistory = s.getDuplicateInHistory() != null && s.getDuplicateInHistory() == 1;

        String type;
        String tip;
        if (invalid) {
            type = "invalid";
            tip = "校验失败：" + s.getErrorMsg();
        } else if (dupFile && dupHistory) {
            type = "both";
            tip = "文件内疑似重复，且历史批次[" + s.getRefHistoryBatch() + "]已成功上送过相同记录";
        } else if (dupFile) {
            type = "file";
            tip = "与本文件内其他记录的医保编号/就诊日期/项目编码/金额完全相同";
        } else if (dupHistory) {
            type = "history";
            tip = "历史批次[" + s.getRefHistoryBatch() + "]已成功上送过相同记录，重复上送可能被平台拒收";
        } else {
            type = "clean";
            tip = "无重复";
        }
        dto.setDuplicateType(type);
        dto.setDuplicateTip(tip);
        return dto;
    }

    // ============================= 确认导入：无重复自动导入，疑似重复需勾选 =============================

    /**
     * 用户确认后正式导入。
     * 规则（后端强约束）：
     *  - 校验失败行：不导入；
     *  - 无重复行：自动导入；
     *  - 文件内重复 / 历史已上送重复：必须在 confirmedDuplicateIds 中显式确认才导入，否则跳过。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResultDTO confirmImport(String checkNo, List<Long> confirmedDuplicateIds,
                                         Long operatorId) {
        // 安全过滤：仅接受属于本预检批次、且确实为疑似重复的有效行ID，忽略越权/伪造ID
        Set<Long> confirmedSafe = confirmedDuplicateIds == null
                ? Collections.emptySet() : new java.util.HashSet<>(confirmedDuplicateIds);

        List<ImportStaging> allRows = importStagingMapper.selectList(
                new LambdaQueryWrapper<ImportStaging>()
                        .eq(ImportStaging::getCheckNo, checkNo)
                        .eq(ImportStaging::getImportedFlag, 0)
                        .orderByAsc(ImportStaging::getRowIndex));

        if (allRows.isEmpty()) {
            throw new IllegalStateException("该预检批次没有可导入的数据，可能已导入或已取消");
        }

        Set<Long> stagingIds = allRows.stream().map(ImportStaging::getId)
                .collect(Collectors.toSet());
        confirmedSafe.retainAll(stagingIds);

        String batchNo = IdUtil.fastSimpleUUID();
        User operator = userMapper.selectById(operatorId);
        String operatorName = operator != null ? operator.getRealName() : "系统";
        String fileName = allRows.stream()
                .map(ImportStaging::getFileName)
                .filter(name -> name != null && !name.isEmpty())
                .findFirst()
                .orElse("预检导入_" + checkNo.substring(0, 8));

        ImportRecord record = new ImportRecord();
        record.setBatchNo(batchNo);
        record.setFileName(fileName);
        record.setStatus(0);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        importRecordMapper.insert(record);

        int invalidCount = 0;
        int skippedDuplicate = 0;
        int successCount = 0;
        List<ExcelData> toInsert = new ArrayList<>();

        for (ImportStaging s : allRows) {
            boolean invalid = s.getValidFlag() != null && s.getValidFlag() == 0;
            boolean dup = (s.getDuplicateInFile() != null && s.getDuplicateInFile() == 1)
                    || (s.getDuplicateInHistory() != null && s.getDuplicateInHistory() == 1);

            if (invalid) {
                invalidCount++;
                continue;
            }
            if (dup && !confirmedSafe.contains(s.getId())) {
                // 疑似重复且用户未确认 -> 跳过，不导入
                skippedDuplicate++;
                continue;
            }

            ExcelData data = new ExcelData();
            data.setDataCode(s.getDataCode());
            data.setName(s.getName());
            data.setIdCard(s.getIdCard());
            data.setPhone(s.getPhone());
            data.setMedicalInsuranceNo(s.getMedicalInsuranceNo());
            data.setVisitDate(s.getVisitDate());
            data.setItemCode(s.getItemCode());
            data.setAmount(s.getAmount());
            data.setAddress(s.getAddress());
            data.setRemark(s.getRemark());
            data.setDedupKey(s.getDedupKey());
            data.setBatchNo(batchNo);
            data.setReportStatus(0);
            toInsert.add(data);
            successCount++;
        }

        for (ExcelData data : toInsert) {
            excelDataMapper.insert(data);
        }

        // 导入完成后清理本次预检的暂存数据（物理删除）
        importStagingMapper.physicalDeleteByCheckNo(checkNo);

        record.setTotalCount(successCount);
        record.setSuccessCount(successCount);
        record.setFailCount(0);
        record.setStatus(1);
        importRecordMapper.updateById(record);

        String message = String.format(
                "导入完成：成功%d条，校验失败排除%d条，未确认疑似重复跳过%d条",
                successCount, invalidCount, skippedDuplicate);
        logger.info("确认导入完成 checkNo={} batchNo={} {}", checkNo, batchNo, message);

        return ImportResultDTO.builder()
                .batchNo(batchNo)
                .totalCount(successCount)
                .successCount(successCount)
                .failCount(0)
                .invalidCount(invalidCount)
                .skippedDuplicateCount(skippedDuplicate)
                .errorList(new ArrayList<>())
                .status("completed")
                .message(message)
                .build();
    }

    /**
     * 取消预检，物理删除暂存数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelCheck(String checkNo) {
        importStagingMapper.physicalDeleteByCheckNo(checkNo);
        logger.info("已取消预检并清理暂存数据 checkNo={}", checkNo);
    }

    // ============================= 既有查询能力 =============================

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
    public Page<ExcelData> getDataByBatch(String batchNo, Integer pageNum, Integer pageSize,
                                          Integer reportStatus) {
        Page<ExcelData> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ExcelData> wrapper = new LambdaQueryWrapper<ExcelData>()
                .eq(ExcelData::getBatchNo, batchNo)
                .orderByAsc(ExcelData::getId);
        if (reportStatus != null) {
            wrapper.eq(ExcelData::getReportStatus, reportStatus);
        }
        return excelDataMapper.selectPage(page, wrapper);
    }

    /**
     * 获取待上报数据
     */
    public List<ExcelData> getPendingReportData(String batchNo) {
        return excelDataMapper.selectByBatchAndStatus(batchNo, 0);
    }
}
