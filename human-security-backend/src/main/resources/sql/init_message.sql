USE dream25;
CREATE TABLE IF NOT EXISTS user_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sender_id BIGINT NOT NULL,
    sender_name VARCHAR(50),
    receiver_id BIGINT NOT NULL,
    receiver_name VARCHAR(50),
    title VARCHAR(100),
    content TEXT,
    read_status TINYINT DEFAULT 0,
    message_type VARCHAR(30) DEFAULT 'NORMAL',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    read_time DATETIME NULL,
    deleted_by_sender TINYINT DEFAULT 0,
    deleted_by_receiver TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息表';
