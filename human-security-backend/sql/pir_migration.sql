-- ============================================================
-- PIR 融合 - 数据库字段升级脚本（MySQL 5.7+ 兼容版）
-- 安全执行（只添加缺失字段，不删除任何数据和表）
-- 使用方式: mysql -h 47.108.58.107 -u root -p dream25 < sql/pir_migration.sql
-- ============================================================

USE `dream25`;

-- 辅助：安全添加列（列存在时跳过，不报错）
-- 使用存储过程实现 MySQL 5.7 兼容的 "ADD COLUMN IF NOT EXISTS"

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_not_exists$$
CREATE PROCEDURE add_column_if_not_exists(
    IN tbl_name VARCHAR(128),
    IN col_name VARCHAR(128),
    IN col_def  TEXT
)
BEGIN
    DECLARE col_count INT DEFAULT 0;
    SELECT COUNT(*) INTO col_count
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'dream25'
      AND TABLE_NAME = tbl_name
      AND COLUMN_NAME = col_name;
    IF col_count = 0 THEN
        SET @stmt = CONCAT('ALTER TABLE `', tbl_name, '` ADD COLUMN `', col_name, '` ', col_def);
        PREPARE st FROM @stmt;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$

DELIMITER ;

-- ============================================================
-- 1. device 表：添加 PIR 相关字段
-- ============================================================
CALL add_column_if_not_exists('device', 'location',         'VARCHAR(100) DEFAULT NULL COMMENT "安装位置"');
CALL add_column_if_not_exists('device', 'device_type',      'VARCHAR(50)  DEFAULT NULL COMMENT "设备类型"');
CALL add_column_if_not_exists('device', 'user_id',          'BIGINT       DEFAULT NULL COMMENT "关联用户ID"');
CALL add_column_if_not_exists('device', 'online_status',    'TINYINT      DEFAULT 0    COMMENT "在线状态：0=离线，1=在线"');
CALL add_column_if_not_exists('device', 'human_detected',   'TINYINT      DEFAULT 0    COMMENT "人体检测：0=无人，1=有人"');
CALL add_column_if_not_exists('device', 'last_report_time', 'DATETIME     DEFAULT NULL COMMENT "最后上报时间"');
CALL add_column_if_not_exists('device', 'remark',           'VARCHAR(500) DEFAULT NULL COMMENT "备注"');

-- ============================================================
-- 2. human_record 表：添加告警关联字段
-- ============================================================
CALL add_column_if_not_exists('human_record', 'device_name', 'VARCHAR(100) DEFAULT NULL                     COMMENT "设备名称"');
CALL add_column_if_not_exists('human_record', 'location',    'VARCHAR(100) DEFAULT NULL                     COMMENT "位置"');
CALL add_column_if_not_exists('human_record', 'is_alarm',    'TINYINT      DEFAULT 0                        COMMENT "是否生成告警：0=否，1=是"');
CALL add_column_if_not_exists('human_record', 'alarm_id',    'BIGINT       DEFAULT NULL                     COMMENT "关联告警ID"');
CALL add_column_if_not_exists('human_record', 'raw_data',    'TEXT         DEFAULT NULL                     COMMENT "原始上报数据"');
CALL add_column_if_not_exists('human_record', 'create_time', 'DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT "创建时间"');

-- ============================================================
-- 3. command_record 表：确保存在
-- ============================================================
CREATE TABLE IF NOT EXISTS `command_record` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `device_code`   VARCHAR(50)  NOT NULL                COMMENT '设备编号',
    `command`       VARCHAR(50)  NOT NULL                COMMENT '指令类型',
    `direction`     VARCHAR(20)  DEFAULT 'SEND'          COMMENT '方向：SEND/REPLY',
    `status`        VARCHAR(20)  DEFAULT 'PENDING'       COMMENT '状态：PENDING/SUCCESS/FAILED',
    `reply_content` TEXT         DEFAULT NULL            COMMENT '设备回复内容',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_device_code` (`device_code`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指令下发记录表';

-- command_record 补充字段（如果表已存在但缺列）
CALL add_column_if_not_exists('command_record', 'direction',     'VARCHAR(20)  DEFAULT "SEND"    COMMENT "方向：SEND/REPLY"');
CALL add_column_if_not_exists('command_record', 'status',        'VARCHAR(20)  DEFAULT "PENDING" COMMENT "状态：PENDING/SUCCESS/FAILED"');
CALL add_column_if_not_exists('command_record', 'reply_content', 'TEXT         DEFAULT NULL       COMMENT "设备回复内容"');

-- device 表补充字段（如果缺列）
CALL add_column_if_not_exists('device', 'device_type', 'VARCHAR(50)  DEFAULT NULL COMMENT "设备类型"');
CALL add_column_if_not_exists('device', 'last_online', 'DATETIME     DEFAULT NULL COMMENT "最后在线时间"');

-- ============================================================
-- 4. 添加 human_record 索引（通过存储过程安全添加）
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_index_if_not_exists$$
CREATE PROCEDURE add_index_if_not_exists(
    IN tbl_name   VARCHAR(128),
    IN idx_name   VARCHAR(128),
    IN idx_col    VARCHAR(128)
)
BEGIN
    DECLARE idx_count INT DEFAULT 0;
    SELECT COUNT(*) INTO idx_count
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'dream25'
      AND TABLE_NAME = tbl_name
      AND INDEX_NAME = idx_name;
    IF idx_count = 0 THEN
        SET @stmt = CONCAT('ALTER TABLE `', tbl_name, '` ADD INDEX `', idx_name, '` (`', idx_col, '`)');
        PREPARE st FROM @stmt;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$

DELIMITER ;

CALL add_index_if_not_exists('human_record', 'idx_report_time', 'report_time');
CALL add_index_if_not_exists('human_record', 'idx_device_code', 'device_code');
CALL add_index_if_not_exists('human_record', 'idx_is_alarm',    'is_alarm');

-- 清理存储过程
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;

-- 完成标记
SELECT 'PIR migration completed successfully' AS result;
