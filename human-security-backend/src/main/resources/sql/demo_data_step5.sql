-- ============================================================
-- 智慧安防 AI 智能体 - 演示数据脚本
-- 用途：为项目演示准备一套完整的示例数据
-- 注意：不 DROP/TRUNCATE/DELETE，仅 INSERT IGNORE 或 ON DUPLICATE KEY UPDATE
-- 可重复执行，不会产生重复脏数据
-- 数据库：dream25 @ 47.108.58.107:3306
--
-- 执行方式：
--   mysql -h 47.108.58.107 -P 3306 -u root -p dream25 < src/main/resources/sql/demo_data_step5.sql
--
-- 执行前请备份：
--   mysqldump -h 47.108.58.107 -P 3306 -u root -p dream25 > dream25_backup_before_demo_data.sql
-- ============================================================

-- ============================================================
-- 一、设备演示数据
--    注意：alarm_record 有外键引用 device.device_code，
--    所以必须先在 device 表中插入设备主记录
-- ============================================================

-- 1a. 先插入 device 主表（FK 依赖）
INSERT IGNORE INTO device (device_code, device_name, location, online_status, defense_status, last_report_time)
VALUES ('human_001', '客厅人体传感器', '客厅', 1, 1, NOW());
INSERT IGNORE INTO device (device_code, device_name, location, online_status, defense_status, last_report_time)
VALUES ('camera_001', '门口摄像头', '门口', 1, 1, NOW());
INSERT IGNORE INTO device (device_code, device_name, location, online_status, defense_status, last_report_time)
VALUES ('HUMAN-BEDROOM-001', '卧室人体感应器', '卧室', 1, 1, NOW());
INSERT IGNORE INTO device (device_code, device_name, location, online_status, defense_status, last_report_time)
VALUES ('DOOR-BALCONY-001', '阳台门磁传感器', '阳台', 0, 1, NOW());
INSERT IGNORE INTO device (device_code, device_name, location, online_status, defense_status, last_report_time)
VALUES ('HUMAN-ENTRY-001', '玄关人体感应器', '玄关', 1, 1, NOW());

-- 1b. 更新 device_status（实时状态）
UPDATE device_status SET online = 1, defense = 1, battery = 86, device_type = '人体感应器'
  WHERE device_code = 'human_001';

UPDATE device_status SET online = 1, defense = 1, battery = 92, device_type = '摄像头'
  WHERE device_code = 'camera_001';

INSERT IGNORE INTO device_status (device_code, device_name, device_type, location, online, defense, battery, last_report_time, updated_at)
VALUES ('HUMAN-BEDROOM-001', '卧室人体感应器', '人体感应器', '卧室', 1, 1, 64, NOW(), NOW());

INSERT IGNORE INTO device_status (device_code, device_name, device_type, location, online, defense, battery, last_report_time, updated_at)
VALUES ('DOOR-BALCONY-001', '阳台门磁传感器', '门磁传感器', '阳台', 0, 1, 42, DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW());

INSERT IGNORE INTO device_status (device_code, device_name, device_type, location, online, defense, battery, last_report_time, updated_at)
VALUES ('HUMAN-ENTRY-001', '玄关人体感应器', '人体感应器', '玄关', 1, 1, 15, NOW(), NOW());


-- ============================================================
-- 二、告警演示数据（alarm_record）
--    handle_status: 0=UNHANDLED, 1=HANDLED, 2=MISREPORT
-- ============================================================

-- 告警1：HIGH 未处理（客厅人体感应器）
INSERT IGNORE INTO alarm_record (id, device_code, device_name, location, alarm_type, alarm_level, alarm_content, handle_status, alarm_time, create_time)
VALUES (101, 'human_001', '客厅人体传感器', '客厅', '人体感应', 'HIGH', '布防状态下检测到异常人体活动，客厅人体传感器在布防期间触发检测', 0, DATE_SUB(NOW(), INTERVAL 30 MINUTE), NOW());

-- 告警2：MEDIUM 未处理（玄关人体感应器）
INSERT IGNORE INTO alarm_record (id, device_code, device_name, location, alarm_type, alarm_level, alarm_content, handle_status, alarm_time, create_time)
VALUES (102, 'HUMAN-ENTRY-001', '玄关人体感应器', '玄关', '人体感应', 'MEDIUM', '玄关区域短时间内多次触发人体感应，可能存在异常出入', 0, DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW());

-- 告警3：HIGH 已处理（阳台门磁）
INSERT IGNORE INTO alarm_record (id, device_code, device_name, location, alarm_type, alarm_level, alarm_content, handle_status, handle_user, handle_time, alarm_time, create_time)
VALUES (103, 'DOOR-BALCONY-001', '阳台门磁传感器', '阳台', '门磁触发', 'HIGH', '阳台门磁传感器在布防状态下检测到门磁异常触发', 1, 'admin', DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW());

-- 告警4：LOW 已处理（卧室人体感应器）
INSERT IGNORE INTO alarm_record (id, device_code, device_name, location, alarm_type, alarm_level, alarm_content, handle_status, handle_user, handle_time, alarm_time, create_time)
VALUES (104, 'HUMAN-BEDROOM-001', '卧室人体感应器', '卧室', '设备状态', 'LOW', '设备短暂离线后已自动恢复，无需人工干预', 1, 'admin', DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 4 HOUR), NOW());


-- ============================================================
-- 三、知识库演示数据（kb_knowledge）
--    已有7条，补充/更新质量
-- ============================================================

-- 更新已有知识条目，增强答案质量
UPDATE kb_knowledge SET
  answer = '进入"设备管理"页面，找到需要布防的设备，点击"布防"按钮即可。布防后设备将进入警戒状态，当检测到异常活动时会自动产生告警。建议离家或夜间休息时对所有传感器设备进行布防。',
  keywords = '布防,如何布防,开启防盗',
  category = '布防'
WHERE id = 1;

UPDATE kb_knowledge SET
  answer = '首先保持冷静，立即查看告警详情了解具体情况。如果确认存在安全威胁，可在告警详情中点击"生成求助信息"，将情况发送给保安或紧急联系人。同时建议开启AI风险研判功能，系统会根据当前状态给出综合建议。',
  keywords = '陌生人,发现陌生人,可疑人员,怎么办',
  category = '异常处理'
WHERE id = 2;

UPDATE kb_knowledge SET
  answer = '进入"设备管理"页面，找到需要撤防的设备，点击"撤防"按钮。撤防后设备将停止警戒监测，适用于家人回家或白天正常活动场景。',
  keywords = '撤防,取消布防,关闭警报',
  category = '布防'
WHERE id = 6;

UPDATE kb_knowledge SET
  answer = '首先检查设备电源是否正常（电池设备需检查电量），其次检查设备网络连接和MQTT服务状态，最后查看设备最后上报时间判断离线时长。如设备已损坏需及时更换。',
  keywords = '设备离线,离线,断线,连接失败',
  category = '设备维护'
WHERE id = 4;

-- 新增知识条目：紧急求助
INSERT IGNORE INTO kb_knowledge (id, question, answer, keywords, category, enabled, created_at)
VALUES (8, '如何发送紧急求助？', '进入"紧急求助"页面，填写设备名称、位置和异常内容后点击"生成求助信息"。系统会自动生成求助文本，可通过"复制求助信息"粘贴到短信或微信，也可点击"发送为站内消息"通知系统内其他用户。', '紧急求助,求助,SOS,求救', '紧急处理', 1, NOW());

-- 新增知识条目：消息中心
INSERT IGNORE INTO kb_knowledge (id, question, answer, keywords, category, enabled, created_at)
VALUES (9, '如何通知保安或物业？', '进入"消息中心"页面，选择当前用户后切换到"发送消息"面板，选择接收人（如小区保安），填写标题和内容后发送。告警消息可自动带入消息中心，消息类型会标记为ALARM或HELP。', '消息,通知,保安,物业,站内信', '消息通知', 1, NOW());

-- 新增知识条目：AI研判
INSERT IGNORE INTO kb_knowledge (id, question, answer, keywords, category, enabled, created_at)
VALUES (10, 'AI安防研判有什么作用？', 'AI安防研判是系统核心智能功能。它会综合分析设备在线状态、布防状态、未处理告警、告警等级、设备电量和离线设备等信息，自动判断当前安防风险等级（LOW/MEDIUM/HIGH/CRITICAL），并给出具体原因和处理建议。在首页点击"立即研判"即可使用，也可在告警详情中对单条告警进行AI分析。', 'AI,研判,风险评估,智能分析', 'AI功能', 1, NOW());

-- 新增知识条目：低电量处理
INSERT IGNORE INTO kb_knowledge (id, question, answer, keywords, category, enabled, created_at)
VALUES (11, '设备低电量怎么办？', '当设备电量低于20%时，系统会提示低电量状态。建议尽快为低电量设备更换电池或充电，避免因断电导致监控盲区。可在设备管理页面查看各设备电量状态，低电量设备行会有红色提示。', '低电量,电池,充电,电量不足', '设备维护', 1, NOW());


-- ============================================================
-- 四、用户演示数据更新（user）
--    更新real_name/nickname显示名（不修改密码）
-- ============================================================

UPDATE user SET real_name = '系统管理员', role = 'admin', status = 1 WHERE id = 1;
UPDATE user SET real_name = '住户用户', role = 'user', status = 1 WHERE id = 2;
UPDATE user SET real_name = '小区保安', role = 'guard', status = 1 WHERE id = 3;


-- ============================================================
-- 五、消息演示数据（user_message）
--    messageType: NORMAL / ALARM / HELP
--    readStatus: 0=未读, 1=已读
--    deletedBySender/deletedByReceiver: 0=未删除
-- ============================================================

-- 消息1：ALARM 类型，管理员→保安（未读）
INSERT IGNORE INTO user_message (id, sender_id, sender_name, receiver_id, receiver_name, title, content, read_status, message_type, create_time, deleted_by_sender, deleted_by_receiver)
VALUES (201, 1, '系统管理员', 3, '小区保安', '智慧安防告警提醒', CONCAT('【智慧安防告警提醒】\n系统检测到异常告警事件。\n\n设备名称：客厅人体传感器\n设备位置：客厅\n告警等级：HIGH\n告警内容：布防状态下检测到异常人体活动\n告警时间：', DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s'), '\n\n请及时查看并处理。'), 0, 'ALARM', DATE_SUB(NOW(), INTERVAL 15 MINUTE), 0, 0);

-- 消息2：HELP 类型，住户→保安（未读）
INSERT IGNORE INTO user_message (id, sender_id, sender_name, receiver_id, receiver_name, title, content, read_status, message_type, create_time, deleted_by_sender, deleted_by_receiver)
VALUES (202, 2, '住户用户', 3, '小区保安', '智慧安防求助提醒', CONCAT('【智慧安防求助提醒】\n检测到异常安防事件，请及时关注。\n\n设备名称：玄关人体感应器\n设备位置：玄关\n告警等级：MEDIUM\n异常内容：玄关区域短时间内多次触发人体感应\n发生时间：', DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s'), '\n\n建议：\n请尽快确认现场情况。如无法及时处理，可联系家属、小区保安或物业人员协助查看。'), 0, 'HELP', DATE_SUB(NOW(), INTERVAL 10 MINUTE), 0, 0);

-- 消息3：NORMAL 类型，保安→住户（已读）
INSERT IGNORE INTO user_message (id, sender_id, sender_name, receiver_id, receiver_name, title, content, read_status, message_type, create_time, read_time, deleted_by_sender, deleted_by_receiver)
VALUES (203, 3, '小区保安', 2, '住户用户', '处理反馈', '已收到您的告警提醒，我将尽快查看现场情况。如有异常会立即通知您。', 1, 'NORMAL', DATE_SUB(NOW(), INTERVAL 5 MINUTE), NOW(), 0, 0);


-- ============================================================
-- 六、聊天记录演示数据（chat_record）
--    补充当天数据，确保首页"今日问答"有统计
-- ============================================================

INSERT IGNORE INTO chat_record (id, user_id, user_message, bot_answer, matched_knowledge_id, category, created_at)
VALUES (301, 1, '系统如何布防？', '进入"设备管理"页面，找到需要布防的设备，点击"布防"按钮即可。布防后设备将进入警戒状态，当检测到异常活动时会自动产生告警。建议离家或夜间休息时对所有传感器设备进行布防。', 1, '布防', NOW());

INSERT IGNORE INTO chat_record (id, user_id, user_message, bot_answer, matched_knowledge_id, category, created_at)
VALUES (302, 1, '当前设备是否在线？', '当前系统共有4台设备。在线设备：客厅人体传感器、卧室人体感应器、玄关人体感应器。离线设备：阳台门磁传感器。请注意检查离线设备状态。', NULL, '设备状态', DATE_SUB(NOW(), INTERVAL 1 MINUTE));

INSERT IGNORE INTO chat_record (id, user_id, user_message, bot_answer, matched_knowledge_id, category, created_at)
VALUES (303, 1, '最近有没有告警？', '当前有2条未处理告警。1条HIGH等级：客厅人体传感器检测到异常人体活动。1条MEDIUM等级：玄关人体感应器多次触发。建议立即查看告警详情并确认现场情况。', NULL, '告警查询', DATE_SUB(NOW(), INTERVAL 2 MINUTE));

INSERT IGNORE INTO chat_record (id, user_id, user_message, bot_answer, matched_knowledge_id, category, created_at)
VALUES (304, 2, '发现陌生人怎么办？', '首先保持冷静，立即查看告警详情了解具体情况。如果确认存在安全威胁，可在告警详情中点击"生成求助信息"，将情况发送给保安或紧急联系人。同时建议开启AI风险研判功能，系统会根据当前状态给出综合建议。', 2, '异常处理', DATE_SUB(NOW(), INTERVAL 30 MINUTE));

INSERT IGNORE INTO chat_record (id, user_id, user_message, bot_answer, matched_knowledge_id, category, created_at)
VALUES (305, 2, '设备离线怎么办？', '首先检查设备电源是否正常（电池设备需检查电量），其次检查设备网络连接和MQTT服务状态，最后查看设备最后上报时间判断离线时长。如设备已损坏需及时更换。', 4, '设备维护', DATE_SUB(NOW(), INTERVAL 1 HOUR));


-- ============================================================
-- 验证 SQL（执行后可用于检查数据）
-- ============================================================
-- SELECT '设备' AS tbl, COUNT(*) AS cnt FROM device_status
-- UNION ALL SELECT '告警', COUNT(*) FROM alarm_record
-- UNION ALL SELECT '知识库', COUNT(*) FROM kb_knowledge
-- UNION ALL SELECT '用户', COUNT(*) FROM user
-- UNION ALL SELECT '消息', COUNT(*) FROM user_message
-- UNION ALL SELECT '聊天', COUNT(*) FROM chat_record;
--
-- SELECT id, device_name, online, defense, battery FROM device_status ORDER BY id;
-- SELECT id, alarm_level, alarm_content, handle_status FROM alarm_record ORDER BY alarm_time DESC;
-- SELECT id, question FROM kb_knowledge WHERE enabled=1 ORDER BY id;
-- SELECT id, title, message_type, read_status FROM user_message ORDER BY create_time DESC;
