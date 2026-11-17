-- 设备状态表 + 测试数据
-- mysql -h 47.108.58.107 -u root -p dream25 < init_device_status.sql

USE `dream25`;

CREATE TABLE IF NOT EXISTS device_status (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_code      VARCHAR(64)  NOT NULL COMMENT '设备编号',
    device_name      VARCHAR(100) NOT NULL COMMENT '设备名称',
    device_type      VARCHAR(50)  COMMENT '设备类型',
    location         VARCHAR(100) COMMENT '设备位置',
    online           TINYINT DEFAULT 0 COMMENT '是否在线 0-离线 1-在线',
    defense          TINYINT DEFAULT 0 COMMENT '是否布防 0-撤防 1-布防',
    battery          INT      DEFAULT 100 COMMENT '电量百分比',
    last_report_time DATETIME COMMENT '最后上报时间',
    updated_at       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备状态表';

-- 测试数据（支持重复执行：先删后插）
DELETE FROM device_status WHERE device_code IN ('human_001', 'camera_001');

INSERT INTO device_status (device_code, device_name, device_type, location, online, defense, battery, last_report_time, updated_at) VALUES
('human_001', '客厅人体传感器', 'human_sensor', '客厅', 1, 1, 86, '2026-07-02 16:40:00', '2026-07-02 16:40:00'),
('camera_001', '门口摄像头',   'camera',       '门口', 1, 1, 92, '2026-07-02 16:40:00', '2026-07-02 16:40:00');
