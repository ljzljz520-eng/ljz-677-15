CREATE TABLE IF NOT EXISTS excel_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data_code VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    id_card VARCHAR(20),
    phone VARCHAR(20),
    medical_insurance_no VARCHAR(60),
    visit_date DATE,
    item_code VARCHAR(60),
    amount DECIMAL(15,2),
    address VARCHAR(200),
    remark VARCHAR(500),
    dedup_key VARCHAR(160),
    batch_no VARCHAR(50) NOT NULL,
    report_status TINYINT DEFAULT 0,
    report_message VARCHAR(500),
    report_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_dedup_key ON excel_data (dedup_key);
CREATE INDEX IF NOT EXISTS idx_history_dedup ON excel_data (dedup_key, report_status);

CREATE TABLE IF NOT EXISTS import_staging (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    check_no VARCHAR(50) NOT NULL,
    file_name VARCHAR(200),
    row_index INT,
    data_code VARCHAR(50),
    name VARCHAR(50),
    id_card VARCHAR(20),
    phone VARCHAR(20),
    medical_insurance_no VARCHAR(60),
    visit_date DATE,
    item_code VARCHAR(60),
    amount DECIMAL(15,2),
    address VARCHAR(200),
    remark VARCHAR(500),
    dedup_key VARCHAR(160),
    valid_flag TINYINT DEFAULT 1,
    error_msg VARCHAR(1000),
    duplicate_in_file TINYINT DEFAULT 0,
    duplicate_in_history TINYINT DEFAULT 0,
    ref_history_id BIGINT,
    ref_history_batch VARCHAR(50),
    ref_history_time DATETIME,
    imported_flag TINYINT DEFAULT 0,
    operator_id BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_staging_dedup_key ON import_staging (dedup_key);

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    real_name VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS import_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(50) NOT NULL,
    file_name VARCHAR(200),
    file_size BIGINT,
    total_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    status TINYINT DEFAULT 0,
    error_details CLOB,
    operator_id BIGINT,
    operator_name VARCHAR(50),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);
