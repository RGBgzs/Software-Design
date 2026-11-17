-- ===================================================
-- 设备状态表 + 测试数据
-- 使用方法：
--   在 DBeaver 中打开本文件，选中全部 SQL 并执行
--   或命令行：mysql -h 47.108.58.107 -u root -p dream25 < update_device_status.sql
-- ===================================================

USE `dream25`;

-- 1. 创建设备状态表（如果不存在才创建，不删除已有数据）
CREATE TABLE IF NOT EXISTS device_status (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT  COMMENT '主键',
    device_code      VARCHAR(64)  NOT NULL              COMMENT '设备编号',
    device_name      VARCHAR(100) NOT NULL              COMMENT '设备名称',
    device_type      VARCHAR(50)  DEFAULT NULL          COMMENT '设备类型（human_sensor / camera / alarm 等）',
    location         VARCHAR(100) DEFAULT NULL          COMMENT '设备位置',
    online           TINYINT      DEFAULT 0             COMMENT '是否在线：0-离线，1-在线',
    defense          TINYINT      DEFAULT 0             COMMENT '是否布防：0-已撤防，1-已布防',
    battery          INT          DEFAULT 100           COMMENT '电量百分比（0-100）',
    last_report_time DATETIME     DEFAULT NULL          COMMENT '最后上报时间',
    updated_at       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备状态表';

-- 2. 插入测试数据（如果设备编号已存在则跳过，不会重复插入）
INSERT INTO device_status (device_code, device_name, device_type, location, online, defense, battery, last_report_time, updated_at)
SELECT 'human_001', '客厅人体传感器', 'human_sensor', '客厅', 1, 1, 86, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM device_status WHERE device_code = 'human_001'
);

INSERT INTO device_status (device_code, device_name, device_type, location, online, defense, battery, last_report_time, updated_at)
SELECT 'camera_001', '门口摄像头', 'camera', '门口', 1, 1, 92, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM device_status WHERE device_code = 'camera_001'
);

-- 3. 验证数据
SELECT * FROM device_status ORDER BY id ASC;
