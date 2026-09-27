package com.excel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.excel.entity.ExcelData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExcelDataMapper extends BaseMapper<ExcelData> {

    @Select("SELECT * FROM excel_data WHERE batch_no = #{batchNo} AND report_status = #{status} AND deleted = 0")
    List<ExcelData> selectByBatchAndStatus(@Param("batchNo") String batchNo, @Param("status") Integer status);

    @Select("SELECT COUNT(*) FROM excel_data WHERE batch_no = #{batchNo} AND deleted = 0")
    Integer countByBatch(@Param("batchNo") String batchNo);

    /**
     * 查询所有已成功上送的记录（仅取重复判断所需字段），用于历史重复检测
     */
    @Select("SELECT medical_insurance_no, visit_date, item_code, amount, batch_no " +
            "FROM excel_data WHERE report_status = 1 AND deleted = 0")
    List<ExcelData> selectReportedRecords();
}
