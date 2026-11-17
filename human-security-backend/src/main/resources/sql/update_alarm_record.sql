-- 告警记录表测试数据补充
-- 注意：alarm_record.device_code 有外键约束，关联 device.device_code
-- 当前 device 表中仅有 device_001（客厅人体感应器）
-- mysql -h 47.108.58.107 -u root -p dream25 < update_alarm_record.sql

USE `dream25`;

-- 表已存在（12列）：id, device_code, device_name, location, alarm_type, alarm_level,
-- alarm_content, handle_status, handle_user, handle_time, alarm_time, create_time
-- handle_status: 0=未处理, 1=已处理, 2=误报

INSERT INTO alarm_record (device_code, device_name, location, alarm_type, alarm_level, alarm_content, handle_status, alarm_time) VALUES
('device_001', '客厅人体感应器', '1栋101客厅', 'INTRUSION', 'HIGH', '布防状态下检测到人体活动（语音测试数据）', 0, NOW()),
('device_001', '客厅人体感应器', '1栋101客厅', 'STRANGER', 'HIGH', '摄像头识别到陌生人入侵（语音测试数据）', 0, NOW()),
('device_001', '客厅人体感应器', '1栋101客厅', 'DEVICE_OFFLINE', 'MEDIUM', '设备曾短暂离线已恢复（语音测试数据）', 1, DATE_SUB(NOW(), INTERVAL 1 DAY));

SELECT COUNT(*) AS total_alarms FROM alarm_record;
SELECT id, device_code, alarm_type, alarm_content, handle_status FROM alarm_record ORDER BY id DESC LIMIT 5;
