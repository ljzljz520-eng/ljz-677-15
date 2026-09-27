package com.excel.mapper;

import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.excel.entity.ExcelData;
import com.excel.entity.ImportStaging;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端验证历史重复识别SQL（嵌入式MariaDB执行真实schema）：
 *  - 只认定 report_status=1（已成功上送）的历史记录
 *  - 同一判重键命中多条历史时取最近一条（窗口函数）
 *  - 批量标记暂存行正确回填引用信息
 */
class HistoryDuplicateSqlIT {

    static DB db;
    static SqlSessionFactory factory;
    static String jdbcUrl;

    @BeforeAll
    static void startDb(@TempDir Path dataDir) throws Exception {
        String arch = System.getProperty("os.arch");
        if (!arch.contains("amd64") && !arch.contains("x86_64")) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                "MariaDB4j只提供x86_64二进制，当前架构 " + arch + " 跳过真实数据库集成测试");
        }
        DBConfigurationBuilder config = DBConfigurationBuilder.newBuilder();
        config.setPort(0);
        config.setDataDir(dataDir.toAbsolutePath().toString());
        db = DB.newEmbeddedDB(config.build());
        db.start();
        jdbcUrl = config.getURL("testdb");

        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS testdb CHARACTER SET utf8mb4");
        }

        DataSource ds = new DataSource() {
            public Connection getConnection() { return getConnection("root", ""); }
            public Connection getConnection(String u, String p) {
                try { return DriverManager.getConnection(jdbcUrl, "root", ""); }
                catch (Exception e) { throw new RuntimeException(e); }
            }
            public java.io.PrintWriter getLogWriter() { return null; }
            public void setLogWriter(java.io.PrintWriter out) { }
            public void setLoginTimeout(int seconds) { }
            public int getLoginTimeout() { return 0; }
            public java.util.logging.Logger getParentLogger() { return null; }
            public <T> T unwrap(Class<T> iface) { return null; }
            public boolean isWrapperFor(Class<?> iface) { return false; }
        };

        // 执行真实schema.sql（拆分按分号执行，仅取建表语句）
        String schema = Files.readString(Path.of("src/main/resources/schema.sql"));
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            for (String stmt : schema.split(";")) {
                String sql = stmt.trim();
                if (sql.toUpperCase().startsWith("CREATE TABLE")) {
                    st.execute(sql);
                }
            }
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        configuration.addMapper(ExcelDataMapper.class);
        configuration.addMapper(ImportStagingMapper.class);
        factory = new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterAll
    static void stopDb() throws Exception {
        if (db != null) {
            db.stop();
        }
    }

    private final LocalDate visit = LocalDate.of(2026, 9, 27);
    private final String key = "YB001|2026-09-27|XM01|100.00";

    @Test
    void historyDuplicateOnlyMatchesLatestReportedAndMarksStaging() {
        try (SqlSession session = factory.openSession(true)) {
            ExcelDataMapper dataMapper = session.getMapper(ExcelDataMapper.class);
            ImportStagingMapper stagingMapper = session.getMapper(ImportStagingMapper.class);

            // 历史：同一判重键两条，一条已上送(旧)、一条已上送(新)、一条待上报(不应参与)
            insertHistory(dataMapper, "OLD-BATCH", 1, LocalDateTime.of(2026, 1, 1, 10, 0));
            insertHistory(dataMapper, "NEW-BATCH", 1, LocalDateTime.of(2026, 9, 1, 10, 0));
            insertHistory(dataMapper, "PENDING-BATCH", 0, null);

            // 暂存：两条同键数据
            Long s1 = insertStaging(stagingMapper, "chk");
            Long s2 = insertStaging(stagingMapper, "chk");

            // 执行Mapper的历史查重
            List<Map<String, Object>> latest =
                    stagingMapper.selectLatestReportedByKeys(List.of(key));
            assertEquals(1, latest.size(), "每个判重键只返回最近一条已上送记录");
            assertEquals("NEW-BATCH", String.valueOf(latest.get(0).get("batchNo")));

            int updated = stagingMapper.markHistoryDuplicate(
                    List.of(s1, s2),
                    ((Number) latest.get(0).get("id")).longValue(),
                    "NEW-BATCH",
                    LocalDateTime.of(2026, 9, 1, 10, 0));
            assertEquals(2, updated);

            ImportStaging row = stagingMapper.selectById(s1);
            assertEquals(1, row.getDuplicateInHistory());
            assertEquals("NEW-BATCH", row.getRefHistoryBatch());

            // 只命中已上送键：传一个从未上送过的键，应返回空
            List<Map<String, Object>> none =
                    stagingMapper.selectLatestReportedByKeys(List.of("NOPE|2026-09-27|X|1.00"));
            assertTrue(none.isEmpty());
        }
    }

    private void insertHistory(ExcelDataMapper mapper, String batch, int status, LocalDateTime time) {
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
        d.setReportStatus(status);
        d.setReportTime(time);
        mapper.insert(d);
        if (status == 1) {
            // report_time通过update填充以模拟真实场景（insert后字段已设值，这里确保落库）
        }
    }

    private Long insertStaging(ImportStagingMapper mapper, String checkNo) {
        ImportStaging s = new ImportStaging();
        s.setCheckNo(checkNo);
        s.setFileName("f.xlsx");
        s.setValidFlag(1);
        s.setDuplicateInFile(0);
        s.setDuplicateInHistory(0);
        s.setImportedFlag(0);
        s.setMedicalInsuranceNo("YB001");
        s.setVisitDate(visit);
        s.setItemCode("XM01");
        s.setAmount(new BigDecimal("100.00"));
        s.setDedupKey(key);
        mapper.insert(s);
        return s.getId();
    }
}
