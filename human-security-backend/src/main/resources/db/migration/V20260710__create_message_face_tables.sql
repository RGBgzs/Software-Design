-- ============================================================
-- 非破坏性 SQL：仅 CREATE TABLE IF NOT EXISTS + ADD COLUMN
-- ============================================================

-- 紧急联系人表
CREATE TABLE IF NOT EXISTS emergency_contact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    contact_user_id BIGINT NOT NULL COMMENT '联系人用户ID',
    contact_username VARCHAR(64) COMMENT '联系人用户名',
    contact_nickname VARCHAR(64) COMMENT '联系人昵称',
    status INT DEFAULT 1 COMMENT '状态 1=正常 0=删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='紧急联系人';

-- 唯一约束：防止重复添加（如果已存在则忽略错误）
-- MySQL 5.7+ 不支持 IF NOT EXISTS for INDEX，由 DataInitializer 安全处理

-- 用户消息表
CREATE TABLE IF NOT EXISTS user_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    sender_name VARCHAR(64) COMMENT '发送者名称',
    receiver_id BIGINT NOT NULL COMMENT '接收者ID',
    receiver_name VARCHAR(64) COMMENT '接收者名称',
    title VARCHAR(200) COMMENT '消息标题',
    content TEXT COMMENT '消息内容',
    read_status INT DEFAULT 0 COMMENT '阅读状态 0=未读 1=已读',
    message_type VARCHAR(32) DEFAULT 'TEXT' COMMENT '消息类型',
    is_emergency INT DEFAULT 0 COMMENT '是否紧急 0=否 1=是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    read_time DATETIME,
    deleted_by_sender INT DEFAULT 0,
    deleted_by_receiver INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息';

-- 已注册人脸人员表
CREATE TABLE IF NOT EXISTS face_person (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    person_id VARCHAR(128) COMMENT '人员标识',
    name VARCHAR(64) NOT NULL COMMENT '姓名',
    face_id INT DEFAULT 0 COMMENT '人脸ID',
    quality_score DOUBLE DEFAULT 0 COMMENT '质量分数',
    device_code VARCHAR(64) COMMENT '设备编号',
    image_path VARCHAR(255) COMMENT '照片路径',
    register_source VARCHAR(32) DEFAULT 'MANUAL' COMMENT '注册来源',
    created_by VARCHAR(64) COMMENT '创建者',
    status INT DEFAULT 1 COMMENT '状态 1=正常 0=删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已注册人脸人员';

-- 人脸识别记录表
CREATE TABLE IF NOT EXISTS face_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    person_id BIGINT COMMENT '人员ID',
    person_name VARCHAR(128) COMMENT '人员姓名',
    person_type VARCHAR(32) DEFAULT 'UNKNOWN' COMMENT '人员类型',
    matched TINYINT DEFAULT 0 COMMENT '是否匹配 1=是 0=否',
    similarity DOUBLE DEFAULT 0 COMMENT '相似度',
    threshold_value DOUBLE DEFAULT 0.4 COMMENT '阈值',
    device_code VARCHAR(64) COMMENT '设备编号',
    location VARCHAR(128) COMMENT '位置',
    risk_level VARCHAR(32) DEFAULT 'LOW' COMMENT '风险等级',
    suggestion VARCHAR(255) COMMENT '建议',
    alarm_id BIGINT COMMENT '关联告警ID',
    image_path VARCHAR(255) COMMENT '照片路径',
    capture_time DATETIME COMMENT '抓拍时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人脸识别记录';
