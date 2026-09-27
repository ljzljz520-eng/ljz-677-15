package com.excel.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.excel.dto.ApiResponse;
import com.excel.dto.ConfirmImportRequest;
import com.excel.dto.ExcelDataDTO;
import com.excel.dto.ImportResultDTO;
import com.excel.dto.PreCheckResultDTO;
import com.excel.dto.ReportResultDTO;
import com.excel.dto.StagingRowDTO;
import com.excel.entity.ExcelData;
import com.excel.entity.ImportRecord;
import com.excel.service.ExcelImportService;
import com.excel.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/excel")
@RequiredArgsConstructor
@Tag(name = "Excel导入管理", description = "Excel数据预检、重复识别、确认导入与上报接口")
public class ExcelController {

    private static final Logger logger = LoggerFactory.getLogger(ExcelController.class);

    private final ExcelImportService excelImportService;
    private final ReportService reportService;

    @PostMapping("/precheck")
    @Operation(summary = "导入预检", description = "上传Excel进行校验与重复识别（文件内重复+历史已上送重复），数据暂存不落正式表")
    public ApiResponse<PreCheckResultDTO> preCheck(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            String validateError = validateExcelFile(file);
            if (validateError != null) {
                return ApiResponse.error(validateError);
            }
            Long userId = (Long) authentication.getPrincipal();
            PreCheckResultDTO result = excelImportService.preCheck(file, userId);
            return ApiResponse.success(result.getMessage(), result);
        } catch (Exception e) {
            logger.error("导入预检失败", e);
            return ApiResponse.error("导入预检失败: " + e.getMessage());
        }
    }

    @GetMapping("/staging/{checkNo}")
    @Operation(summary = "获取预检清单", description = "分页获取预检数据及后端判定的重复标记。category: invalid/file/history/clean")
    public ApiResponse<Page<StagingRowDTO>> getStaging(
            @PathVariable String checkNo,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<StagingRowDTO> page = excelImportService.getStagingPage(
                checkNo, category, pageNum, pageSize);
        return ApiResponse.success(page);
    }

    @PostMapping("/confirm/{checkNo}")
    @Operation(summary = "确认导入", description = "无重复数据自动导入；疑似重复数据须在confirmedDuplicateIds中确认后才导入")
    public ApiResponse<ImportResultDTO> confirmImport(
            @PathVariable String checkNo,
            @RequestBody(required = false) ConfirmImportRequest request,
            Authentication authentication) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            List<Long> ids = request == null ? null : request.getConfirmedDuplicateIds();
            ImportResultDTO result = excelImportService.confirmImport(checkNo, ids, userId);
            return ApiResponse.success(result.getMessage(), result);
        } catch (IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            logger.error("确认导入失败", e);
            return ApiResponse.error("确认导入失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/precheck/{checkNo}")
    @Operation(summary = "取消预检", description = "放弃本次导入并清理暂存数据")
    public ApiResponse<Void> cancelPreCheck(@PathVariable String checkNo) {
        try {
            excelImportService.cancelCheck(checkNo);
            return ApiResponse.success("已取消", null);
        } catch (Exception e) {
            logger.error("取消预检失败", e);
            return ApiResponse.error("取消预检失败: " + e.getMessage());
        }
    }

    @GetMapping("/records")
    @Operation(summary = "获取导入记录", description = "分页获取导入记录列表")
    public ApiResponse<Page<ImportRecord>> getImportRecords(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ImportRecord> page = excelImportService.getImportRecords(pageNum, pageSize);
        return ApiResponse.success(page);
    }

    @GetMapping("/data/{batchNo}")
    @Operation(summary = "获取批次数据", description = "根据批次号分页获取数据")
    public ApiResponse<Page<ExcelData>> getDataByBatch(
            @PathVariable String batchNo,
            @RequestParam(required = false) Integer reportStatus,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ExcelData> page = excelImportService.getDataByBatch(
                batchNo, pageNum, pageSize, reportStatus);
        return ApiResponse.success(page);
    }

    @PostMapping("/report/{batchNo}")
    @Operation(summary = "上报数据", description = "将指定批次数据上报到国家平台")
    public ApiResponse<ReportResultDTO> reportData(@PathVariable String batchNo) {
        try {
            ReportResultDTO result = reportService.reportToNationalPlatform(batchNo);
            return ApiResponse.success("上报完成", result);
        } catch (Exception e) {
            logger.error("数据上报失败", e);
            return ApiResponse.error("上报失败: " + e.getMessage());
        }
    }

    @GetMapping("/report/failed/{batchNo}")
    @Operation(summary = "获取上报失败数据", description = "获取指定批次上报失败的数据")
    public ApiResponse<List<ExcelData>> getFailedReportData(@PathVariable String batchNo) {
        List<ExcelData> failedList = reportService.getFailedReportData(batchNo);
        return ApiResponse.success(failedList);
    }

    @PostMapping("/report/retry/{batchNo}")
    @Operation(summary = "重试上报", description = "重新上报失败的数据")
    public ApiResponse<ReportResultDTO> retryReport(@PathVariable String batchNo) {
        try {
            // 先重置失败数据状态
            reportService.resetFailedData(batchNo);
            // 再次上报
            ReportResultDTO result = reportService.reportToNationalPlatform(batchNo);
            return ApiResponse.success("重新上报完成", result);
        } catch (Exception e) {
            logger.error("重新上报失败", e);
            return ApiResponse.error("重新上报失败: " + e.getMessage());
        }
    }

    @GetMapping("/template")
    @Operation(summary = "下载导入模板", description = "下载Excel导入模板")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("医保数据导入模板", StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        List<ExcelDataDTO> templateData = new ArrayList<>();
        ExcelDataDTO example = new ExcelDataDTO();
        example.setDataCode("DATA001");
        example.setName("张三");
        example.setIdCard("110101199001011234");
        example.setPhone("13800138000");
        example.setAmount(new BigDecimal("128.50"));
        example.setAddress("北京市朝阳区xxx街道");
        example.setRemark("示例数据");
        example.setMedicalInsuranceNo("YB11010119900101001");
        example.setVisitDate(LocalDate.of(2026, 9, 27));
        example.setItemCode("XM0101");
        templateData.add(example);

        EasyExcel.write(response.getOutputStream(), ExcelDataDTO.class)
                .sheet("医保数据导入模板")
                .doWrite(templateData);
    }

    @GetMapping("/export/errors/{batchNo}")
    @Operation(summary = "导出错误数据", description = "导出上报失败的数据为Excel")
    public void exportErrors(@PathVariable String batchNo, HttpServletResponse response) throws IOException {
        List<ExcelData> failedList = reportService.getFailedReportData(batchNo);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("上报失败数据_" + batchNo, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        List<ExcelDataDTO> exportList = new ArrayList<>();
        for (ExcelData data : failedList) {
            ExcelDataDTO dto = new ExcelDataDTO();
            dto.setDataCode(data.getDataCode());
            dto.setName(data.getName());
            dto.setIdCard(data.getIdCard());
            dto.setPhone(data.getPhone());
            dto.setAmount(data.getAmount());
            dto.setAddress(data.getAddress());
            dto.setRemark(data.getRemark());
            dto.setMedicalInsuranceNo(data.getMedicalInsuranceNo());
            dto.setVisitDate(data.getVisitDate());
            dto.setItemCode(data.getItemCode());
            dto.setErrorMsg(data.getReportMessage());
            exportList.add(dto);
        }

        EasyExcel.write(response.getOutputStream(), ExcelDataDTO.class)
                .sheet("上报失败数据")
                .doWrite(exportList);
    }

    private String validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "请选择要上传的文件";
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
            return "仅支持Excel文件（.xlsx或.xls）";
        }
        return null;
    }
}
