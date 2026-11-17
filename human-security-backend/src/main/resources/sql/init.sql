-- 在远程数据库 dream25 上执行
-- mysql -h 47.108.58.107 -u root -p dream25 < init.sql

USE dream25;

CREATE TABLE IF NOT EXISTS human_record (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_code     VARCHAR(64) NOT NULL COMMENT '设备编号',
    human_detected  TINYINT NOT NULL COMMENT '是否检测到人体 0-否 1-是',
    defense_status  TINYINT DEFAULT 0 COMMENT '布防状态 0-撤防 1-布防',
    report_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上报时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人体检测记录表';
