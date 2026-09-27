package com.excel.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.excel.dto.ImportResultDTO;
import com.excel.entity.ExcelData;
import com.excel.entity.ImportStaging;
import com.excel.entity.User;
import com.excel.mapper.ExcelDataMapper;
import com.excel.mapper.ImportRecordMapper;
import com.excel.mapper.ImportStagingMapper;
import com.excel.mapper.UserMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务层端到端（H2 MySQL兼容模式）：
 * 1) 历史只比对已成功上送记录，同键取最近一条（窗口函数）
 * 2) 确认导入：疑似重复未勾选跳过、勾选后导入；无重复自动导入
 */
class ConfirmImportH2Test {

    static DataSource dataSource;
    static SqlSessionFactory factory;
    SqlSession session;
    ExcelDataMapper dataMapper;
    ImportStagingMapper stagingMapper;
    ImportRecordMapper recordMapper;
    UserMapper userMapper;
    ExcelImportService service;

    @BeforeAll
    static void init() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:dedup;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        dataSource = ds;

        String ddl;
        try (InputStream in = ConfirmImportH2Test.class.getResourceAsStream("/schema-h2.sql")) {
            ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            for (String sql : ddl.split(";")) {
                if (!sql.trim().isEmpty()) {
                    st.execute(sql);
                }
            }
        }

        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setEnvironment(new Environment("h2", new JdbcTransactionFactory(), ds));
        cfg.addMapper(ExcelDataMapper.class);
        cfg.addMapper(ImportStagingMapper.class);
        cfg.addMapper(ImportRecordMapper.class);
        cfg.addMapper(UserMapper.class);
        factory = new SqlSessionFactoryBuilder().build(cfg);
    }

    @BeforeEach
    void open() {
        session = factory.openSession(false);
        dataMapper = session.getMapper(ExcelDataMapper.class);
        stagingMapper = session.getMapper(ImportStagingMapper.class);
        recordMapper = session.getMapper(ImportRecordMapper.class);
        userMapper = session.getMapper(UserMapper.class);
        // 清空表，保证用例间数据隔离
        dataMapper.delete(null);
        stagingMapper.delete(null);
        recordMapper.delete(null);
        userMapper.delete(null);
        service = new ExcelImportService(dataMapper, recordMapper, stagingMapper, userMapper);
    }

    @AfterEach
    void close() {
        session.close();
    }

    private final LocalDate visit = LocalDate.of(2026, 9, 27);
    private final String key = "YB001|2026-09-27|XM01|100.00";

    @Test
    void historyLookupUsesOnlyLatestReported() {
        insertHistory("OLD", 1, LocalDateTime.of(2026, 1, 1, 9, 0));
        insertHistory("NEW", 1, LocalDateTime.of(2026, 9, 1, 9, 0));
        insertHistory("PENDING", 0, null);

        List<java.util.Map<String, Object>> rows =
                stagingMapper.selectLatestReportedByKeys(List.of(key));
        assertEquals(1, rows.size());
        // 列名大小写因库而异，做不敏感读取
        Object batch = rows.get(0).keySet().stream()
                .filter(k -> k.equalsIgnoreCase("batchNo"))
                .map(rows.get(0)::get).findFirst().orElse(null);
        assertEquals("NEW", String.valueOf(batch));
    }

    @Test
    void unconfirmedDuplicatesAreSkipped() {
        User user = newUser("u");
        insertHistory("HIST", 1, LocalDateTime.of(2026, 8, 1, 9, 0));

        String checkNo = "chk-confirm";
        // 无重复
        insertStaging(checkNo, key + ".C");
        // 文件内重复：同键两条，第二条标记dup
        insertStaging(checkNo, key + ".F");
        Long f2 = insertStaging(checkNo, key + ".F");
        stagingMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ImportStaging>()
                .eq(ImportStaging::getId, f2).set(ImportStaging::getDuplicateInFile, 1));
        // 历史重复
        Long h1 = insertStaging(checkNo, key);
        stagingMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ImportStaging>()
                .eq(ImportStaging::getId, h1).set(ImportStaging::getDuplicateInHistory, 1));

        ImportResultDTO r = service.confirmImport(checkNo, List.of(), user.getId());
        session.commit();

        assertEquals(2, r.getSuccessCount(), "无重复行 + 文件内重复首条 自动导入");
        assertEquals(2, r.getSkippedDuplicateCount(), "f2(文件内重复) 与 h1(历史重复) 未确认应跳过");

        List<ExcelData> imported = dataMapper.selectList(null);
        long newRowsWithHistoryKey = imported.stream()
                .filter(d -> !"HIST".equals(d.getBatchNo()))
                .filter(d -> key.equals(d.getDedupKey())).count();
        assertEquals(0, newRowsWithHistoryKey, "未确认的历史重复不得导入新批次");
        assertEquals(0L, stagingMapper.selectCount(null), "确认后应清理暂存数据");
    }

    @Test
    void confirmedHistoryDuplicateIsImported() {
        User user = newUser("u2");
        insertHistory("HIST", 1, LocalDateTime.of(2026, 8, 1, 9, 0));
        String checkNo = "chk-confirm2";
        Long h1 = insertStaging(checkNo, key);
        stagingMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ImportStaging>()
                .eq(ImportStaging::getId, h1).set(ImportStaging::getDuplicateInHistory, 1));

        ImportResultDTO r = service.confirmImport(checkNo, List.of(h1), user.getId());
        session.commit();

        assertEquals(1, r.getSuccessCount(), "用户确认后历史重复允许导入");
        assertEquals(0, r.getSkippedDuplicateCount());
        assertTrue(dataMapper.selectList(null).stream().anyMatch(d -> key.equals(d.getDedupKey())));
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + System.nanoTime());
        user.setPassword("x");
        user.setRealName("测试员");
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);
        return user;
    }

    private void insertHistory(String batch, int status, LocalDateTime time) {
        ExcelData d = new ExcelData();
        d.setDataCode("C" + System.nanoTime());
        d.setName("张三");
        d.setMedicalInsuranceNo("YB001");
        d.setVisitDate(visit);
        d.setItemCode("XM01");
        d.setAmount(new BigDecimal("100.00"));
        d.setDedupKey(key);
        d.setBatchNo(batch);
        d.setReportStatus(status);
        d.setReportTime(time);
        dataMapper.insert(d);
    }

    private Long insertStaging(String checkNo, String dedupKey) {
        ImportStaging s = new ImportStaging();
        s.setCheckNo(checkNo);
        s.setFileName("f.xlsx");
        s.setValidFlag(1);
        s.setDuplicateInFile(0);
        s.setDuplicateInHistory(0);
        s.setImportedFlag(0);
        s.setMedicalInsuranceNo("YB" + Math.abs(dedupKey.hashCode()));
        s.setVisitDate(visit);
        s.setItemCode("XM");
        s.setAmount(new BigDecimal("100.00"));
        s.setDedupKey(dedupKey);
        s.setDataCode("D" + System.nanoTime());
        s.setName("测试");
        stagingMapper.insert(s);
        return s.getId();
    }
}
