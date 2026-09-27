-- 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    `real_name` VARCHAR(50) COMMENT '真实姓名',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- Excel数据表
CREATE TABLE IF NOT EXISTS `excel_data` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `data_code` VARCHAR(50) NOT NULL COMMENT '数据编号',
    `name` VARCHAR(50) NOT NULL COMMENT '姓名',
    `id_card` VARCHAR(20) COMMENT '身份证号',
    `phone` VARCHAR(20) COMMENT '手机号',
    `medical_insurance_no` VARCHAR(60) COMMENT '医保编号（参保人医保编号）',
    `visit_date` DATE COMMENT '就诊日期',
    `item_code` VARCHAR(60) COMMENT '项目编码（诊疗/收费项目编码）',
    `amount` DECIMAL(15,2) COMMENT '金额',
    `address` VARCHAR(200) COMMENT '地址',
    `remark` VARCHAR(500) COMMENT '备注',
    `dedup_key` VARCHAR(160) COMMENT '判重键：规范化的医保编号|就诊日期|项目编码|金额',
    `batch_no` VARCHAR(50) NOT NULL COMMENT '导入批次号',
    `report_status` TINYINT DEFAULT 0 COMMENT '上报状态：0-待上报 1-已上报 2-上报失败',
    `report_message` VARCHAR(500) COMMENT '上报结果信息',
    `report_time` DATETIME COMMENT '上报时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    PRIMARY KEY (`id`),
    INDEX `idx_batch_no` (`batch_no`),
    INDEX `idx_report_status` (`report_status`),
    INDEX `idx_data_code` (`data_code`),
    INDEX `idx_dedup_key` (`dedup_key`),
    INDEX `idx_history_dedup` (`dedup_key`, `report_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Excel数据表';

-- 导入暂存表（预检数据，用户确认疑似重复后才转入 excel_data）
CREATE TABLE IF NOT EXISTS `import_staging` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `check_no` VARCHAR(50) NOT NULL COMMENT '预检批次号（一次文件预检唯一）',
    `file_name` VARCHAR(200) COMMENT '原始文件名',
    `row_index` INT COMMENT 'Excel行号（含表头，从2开始）',
    `data_code` VARCHAR(50) COMMENT '数据编号',
    `name` VARCHAR(50) COMMENT '姓名',
    `id_card` VARCHAR(20) COMMENT '身份证号',
    `phone` VARCHAR(20) COMMENT '手机号',
    `medical_insurance_no` VARCHAR(60) COMMENT '医保编号',
    `visit_date` DATE COMMENT '就诊日期',
    `item_code` VARCHAR(60) COMMENT '项目编码',
    `amount` DECIMAL(15,2) COMMENT '金额',
    `address` VARCHAR(200) COMMENT '地址',
    `remark` VARCHAR(500) COMMENT '备注',
    `dedup_key` VARCHAR(160) COMMENT '判重键：规范化的医保编号|就诊日期|项目编码|金额',
    `valid_flag` TINYINT DEFAULT 1 COMMENT '是否通过校验：0-无效 1-有效',
    `error_msg` VARCHAR(1000) COMMENT '校验错误信息',
    `duplicate_in_file` TINYINT DEFAULT 0 COMMENT '文件内疑似重复：0-否 1-是',
    `duplicate_in_history` TINYINT DEFAULT 0 COMMENT '历史已上送重复：0-否 1-是',
    `ref_history_id` BIGINT COMMENT '命中的历史已上送数据ID',
    `ref_history_batch` VARCHAR(50) COMMENT '命中的历史已上送数据批次号',
    `ref_history_time` DATETIME COMMENT '命中的历史记录上送时间',
    `imported_flag` TINYINT DEFAULT 0 COMMENT '是否已转入正式表：0-否 1-是',
    `operator_id` BIGINT COMMENT '操作人ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    PRIMARY KEY (`id`),
    INDEX `idx_check_no` (`check_no`),
    INDEX `idx_staging_dedup_key` (`dedup_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入暂存表';

-- 导入记录表
CREATE TABLE IF NOT EXISTS `import_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_no` VARCHAR(50) NOT NULL COMMENT '批次号',
    `file_name` VARCHAR(200) COMMENT '文件名',
    `file_size` BIGINT COMMENT '文件大小（字节）',
    `total_count` INT DEFAULT 0 COMMENT '总记录数',
    `success_count` INT DEFAULT 0 COMMENT '成功数量',
    `fail_count` INT DEFAULT 0 COMMENT '失败数量',
    `status` TINYINT DEFAULT 0 COMMENT '导入状态：0-处理中 1-完成 2-失败',
    `error_details` TEXT COMMENT '错误详情',
    `operator_id` BIGINT COMMENT '操作人ID',
    `operator_name` VARCHAR(50) COMMENT '操作人姓名',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_no` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入记录表';

-- 管理员用户由应用启动时通过 DataInitializer 自动创建
-- 账号: admin  密码: admin123
