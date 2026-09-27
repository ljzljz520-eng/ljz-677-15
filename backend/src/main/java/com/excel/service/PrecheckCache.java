package com.excel.service;

import com.excel.dto.ExcelDataDTO;
import com.excel.entity.ExcelData;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预检数据暂存
 * 预检通过后数据暂存内存，用户确认后才正式入库；
 * 暂存数据30分钟过期，过期后需重新上传预检
 */
@Component
public class PrecheckCache {

    private static final Logger logger = LoggerFactory.getLogger(PrecheckCache.class);

    /**
     * 暂存有效期：30分钟
     */
    private static final long EXPIRE_MILLIS = 30 * 60 * 1000L;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 暂存预检数据
     */
    public void put(String checkNo, List<ExcelData> validList, List<ExcelDataDTO> errorList,
                    int totalCount, String fileName) {
        cleanExpired();
        cache.put(checkNo, new CacheEntry(validList, errorList, totalCount, fileName,
                System.currentTimeMillis()));
        logger.debug("预检数据已暂存: checkNo={}, 有效数据{}条", checkNo, validList.size());
    }

    /**
     * 获取暂存的预检数据
     */
    public CacheEntry get(String checkNo) {
        cleanExpired();
        return cache.get(checkNo);
    }

    /**
     * 移除暂存数据（确认导入后调用）
     */
    public void remove(String checkNo) {
        cache.remove(checkNo);
    }

    /**
     * 清理过期数据
     */
    private void cleanExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> {
            boolean expired = now - entry.getValue().getCreateTime() > EXPIRE_MILLIS;
            if (expired) {
                logger.debug("预检暂存数据已过期: checkNo={}", entry.getKey());
            }
            return expired;
        });
    }

    /**
     * 暂存的预检数据
     */
    @Getter
    public static class CacheEntry {
        private final List<ExcelData> validList;
        private final List<ExcelDataDTO> errorList;
        private final int totalCount;
        private final String fileName;
        private final long createTime;

        public CacheEntry(List<ExcelData> validList, List<ExcelDataDTO> errorList,
                          int totalCount, String fileName, long createTime) {
            this.validList = validList;
            this.errorList = errorList;
            this.totalCount = totalCount;
            this.fileName = fileName;
            this.createTime = createTime;
        }
    }
}
