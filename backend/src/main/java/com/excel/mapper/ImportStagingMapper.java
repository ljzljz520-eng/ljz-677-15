package com.excel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.excel.entity.ImportStaging;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ImportStagingMapper extends BaseMapper<ImportStaging> {

    /**
     * 预检取消或确认导入后，物理清理本次暂存数据
     */
    @Delete("DELETE FROM import_staging WHERE check_no = #{checkNo}")
    int physicalDeleteByCheckNo(@Param("checkNo") String checkNo);

    /**
     * 按判重键批量查询历史中“已成功上送（report_status=1）”的最新一条记录。
     * 只比对正式表，且只认定成功上送过的数据。
     *
     * @param dedupKeys 判重键集合
     * @return 每个判重键对应一条最近成功上送记录：dedupKey/id/batchNo/reportTime
     */
    @Select({
            "<script>",
            "SELECT dedup_key AS dedupKey, id, batch_no AS batchNo, report_time AS reportTime",
            "FROM (",
            "  SELECT d.*, ROW_NUMBER() OVER (PARTITION BY d.dedup_key ORDER BY d.report_time DESC, d.id DESC) AS rn",
            "  FROM excel_data d",
            "  WHERE d.deleted = 0 AND d.report_status = 1 AND d.dedup_key IN",
            "  <foreach collection='dedupKeys' item='k' open='(' separator=',' close=')'>#{k}</foreach>",
            ") t WHERE rn = 1",
            "</script>"
    })
    List<Map<String, Object>> selectLatestReportedByKeys(@Param("dedupKeys") List<String> dedupKeys);

    /**
     * 批量将暂存行标记为命中历史已上送记录。
     *
     * @param idList    暂存行ID集合（同一判重键下的所有暂存行）
     * @param historyId 命中的历史记录ID
     * @param batchNo   命中的历史批次号
     */
    @Update({
            "<script>",
            "UPDATE import_staging",
            "SET duplicate_in_history = 1,",
            "    ref_history_id = #{historyId},",
            "    ref_history_batch = #{batchNo},",
            "    ref_history_time = #{reportTime}",
            "WHERE deleted = 0 AND id IN",
            "<foreach collection='idList' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    int markHistoryDuplicate(@Param("idList") List<Long> idList,
                             @Param("historyId") Long historyId,
                             @Param("batchNo") String batchNo,
                             @Param("reportTime") java.time.LocalDateTime reportTime);
}
