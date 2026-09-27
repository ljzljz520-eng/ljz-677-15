package com.excel.listener;

import com.alibaba.excel.EasyExcel;
import com.excel.entity.ImportStaging;
import com.excel.mapper.ImportStagingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.invocation.InvocationOnMock;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * 用真实EasyExcel解析 + Mockito打桩Mapper，验证：
 * 文件内重复标记（医保编号+就诊日期+项目编码+金额）及必填校验
 */
class StagingDataListenerTest {

    @Test
    void parsesAndMarksInFileDuplicates(@TempDir Path dir) {
        List<ImportStaging> saved = new ArrayList<>();
        AtomicLong seq = new AtomicLong(1);

        ImportStagingMapper mapper = mock(ImportStagingMapper.class);
        doAnswer((InvocationOnMock inv) -> {
            ImportStaging s = inv.getArgument(0);
            s.setId(seq.getAndIncrement());
            saved.add(s);
            return 1;
        }).when(mapper).insert(any(ImportStaging.class));

        List<List<String>> head = new ArrayList<>();
        String[] titles = {"数据编号", "姓名", "身份证号", "手机号", "金额", "地址", "备注", "医保编号", "就诊日期", "项目编码"};
        for (String t : titles) {
            head.add(List.of(t));
        }

        List<List<Object>> data = new ArrayList<>();
        data.add(row("D1", "张三", "", "", "100.00", "", "", "YB001", "2026-09-27", "XM01"));
        // 与行1四要素相同（大小写/分隔符不同）-> 文件内重复
        data.add(row("D2", "李四", "", "", "100.00", "", "", "yb001", "2026/9/27", "xm01"));
        // 金额不同 -> 不重复
        data.add(row("D3", "王五", "", "", "200.00", "", "", "YB001", "2026-09-27", "XM01"));
        // 缺医保编号 -> 校验失败
        data.add(row("D4", "赵六", "", "", "100.00", "", "", "", "2026-09-27", "XM01"));
        // 日期非法 -> 校验失败
        data.add(row("D5", "孙七", "", "", "100.00", "", "", "YB009", "abc", "XM09"));

        File excel = dir.resolve("test.xlsx").toFile();
        EasyExcel.write(excel).head(head).sheet("data").doWrite(data);

        StagingDataListener listener = new StagingDataListener(mapper, "chk1", "test.xlsx", 1L);
        EasyExcel.read(excel, com.excel.dto.ExcelDataDTO.class, listener)
                .sheet().headRowNumber(1).doRead();

        assertEquals(5, listener.getTotalCount());
        assertEquals(2, listener.getInvalidCount(), "行4、行5校验失败");
        assertEquals(1, listener.getDuplicateInFileCount(), "仅行2为文件内重复");

        assertEquals(0, saved.get(0).getDuplicateInFile());
        assertEquals(1, saved.get(1).getDuplicateInFile(), "大小写/分隔符归一化后应判为重复");
        assertEquals(0, saved.get(2).getDuplicateInFile());
        assertEquals(0, saved.get(3).getValidFlag());
        assertEquals(0, saved.get(4).getValidFlag());
        assertNotNull(saved.get(0).getDedupKey());
        assertEquals(saved.get(0).getDedupKey(), saved.get(1).getDedupKey());
        assertNotEquals(saved.get(0).getDedupKey(), saved.get(2).getDedupKey());
    }

    private List<Object> row(String... vals) {
        return new ArrayList<>(List.of(vals));
    }
}
