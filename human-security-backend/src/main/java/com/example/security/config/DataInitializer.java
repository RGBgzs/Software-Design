package com.example.security.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 非破坏性数据库初始化：仅 CREATE TABLE IF NOT EXISTS + 安全索引
 * 不会 DROP / TRUNCATE / DELETE 任何数据
 *
 * 方案 A：JdbcTemplate 直接执行 Java 字符串，不依赖 SQL 文件拆分
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ── 启动诊断用的表清单 ──
    private static final List<String> REQUIRED_TABLES = Arrays.asList(
        "emergency_contact", "user_message", "face_person", "face_record",
        "emergency_help_event", "emergency_help_receiver"
    );

    @Override
    public void run(String... args) {
        // ═══════════════════════════════════════════
        //  0. 启动诊断：输出数据库连接信息
        // ═══════════════════════════════════════════
        printDatabaseInfo();

        // ═══════════════════════════════════════════
        //  1. 建表（CREATE TABLE IF NOT EXISTS）
        // ═══════════════════════════════════════════
        createEmergencyContactTable();
        createUserMessageTable();
        createFacePersonTable();
        createFaceRecordTable();
        createEmergencyHelpEventTable();
        createEmergencyHelpReceiverTable();

        // ═══════════════════════════════════════════
        //  2. 校验：确认每张表真实存在
        // ═══════════════════════════════════════════
        boolean allTablesOk = true;
        for (String tableName : REQUIRED_TABLES) {
            boolean exists = tableExists(tableName);
            log.info("[DataInitializer] table {} exists={}", tableName, exists);
            if (!exists) {
                allTablesOk = false;
                log.error("[DataInitializer] 严重：表 {} 创建失败，请检查数据库权限和引擎状态", tableName);
            }
        }

        if (!allTablesOk) {
            log.error("[DataInitializer] ========================================");
            log.error("[DataInitializer] 部分表创建失败，消息中心/人脸模块可能不可用");
            log.error("[DataInitializer] 请检查 MySQL 用户权限和 InnoDB 引擎状态");
            log.error("[DataInitializer] ========================================");
        }

        // ═══════════════════════════════════════════
        //  3. 安全补列（旧表可能缺少列）
        // ═══════════════════════════════════════════
        ensureAllMissingColumns();

        // ═══════════════════════════════════════════
        //  4. 安全创建索引（仅在表和列都存在时）
        // ═══════════════════════════════════════════
        createAllIndexes();

        // ═══════════════════════════════════════════
        //  5. 清理重复数据（仅在表存在时）
        // ═══════════════════════════════════════════
        if (tableExists("emergency_contact")) {
            cleanDuplicateContacts();
        }

        log.info("[DataInitializer] Database initialization completed. allTablesOk={}", allTablesOk);
    }

    // ═══════════════════════════════════════════
    //  数据库连接诊断
    // ═══════════════════════════════════════════

    private void printDatabaseInfo() {
        try {
            String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            log.info("[DataInitializer] ========================================");
            log.info("[DataInitializer] 当前数据库: {}", dbName);
            log.info("[DataInitializer] MySQL 版本: {}", version);
            log.info("[DataInitializer] ========================================");
        } catch (Exception e) {
            log.warn("[DataInitializer] 无法获取数据库信息: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════
    //  表存在性检查
    // ═══════════════════════════════════════════

    private boolean tableExists(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, tableName
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("[DataInitializer] 检查表 {} 存在性失败: {}", tableName, e.getMessage());
            return false;
        }
    }

    private boolean indexExists(String tableName, String indexName) {
        try {
            if (!tableExists(tableName)) return false;
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics " +
                "WHERE table_schema = DATABASE() " +
                "AND table_name = ? AND index_name = ?",
                Integer.class, tableName, indexName
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("[DataInitializer] 检查索引 {}.{} 失败: {}", tableName, indexName, e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════
    //  建表：emergency_contact
    // ═══════════════════════════════════════════

    private void createEmergencyContactTable() {
        String sql =
            "CREATE TABLE IF NOT EXISTS emergency_contact (" +
            "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
            "  user_id BIGINT NOT NULL COMMENT '用户ID'," +
            "  contact_user_id BIGINT NOT NULL COMMENT '联系人用户ID'," +
            "  contact_username VARCHAR(64) COMMENT '联系人用户名'," +
            "  contact_nickname VARCHAR(64) COMMENT '联系人昵称'," +
            "  status INT DEFAULT 1 COMMENT '状态 1=正常 0=删除'," +
            "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='紧急联系人'";
        try {
            jdbcTemplate.execute(sql);
            log.info("[DataInitializer] emergency_contact 建表语句执行成功");
        } catch (Exception e) {
            log.error("[DataInitializer] emergency_contact 建表失败: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════
    //  建表：user_message
    // ═══════════════════════════════════════════

    private void createUserMessageTable() {
        String sql =
            "CREATE TABLE IF NOT EXISTS user_message (" +
            "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
            "  sender_id BIGINT NOT NULL COMMENT '发送者ID'," +
            "  sender_name VARCHAR(64) COMMENT '发送者名称'," +
            "  receiver_id BIGINT NOT NULL COMMENT '接收者ID'," +
            "  receiver_name VARCHAR(64) COMMENT '接收者名称'," +
            "  title VARCHAR(200) COMMENT '消息标题'," +
            "  content TEXT COMMENT '消息内容'," +
            "  read_status INT DEFAULT 0 COMMENT '阅读状态 0=未读 1=已读'," +
            "  message_type VARCHAR(32) DEFAULT 'TEXT' COMMENT '消息类型'," +
            "  is_emergency INT DEFAULT 0 COMMENT '是否紧急 0=否 1=是'," +
            "  create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "  read_time DATETIME," +
            "  deleted_by_sender INT DEFAULT 0," +
            "  deleted_by_receiver INT DEFAULT 0" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息'";
        try {
            jdbcTemplate.execute(sql);
            log.info("[DataInitializer] user_message 建表语句执行成功");
        } catch (Exception e) {
            log.error("[DataInitializer] user_message 建表失败: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════
    //  建表：face_person
    // ═══════════════════════════════════════════

    private void createFacePersonTable() {
        String sql =
            "CREATE TABLE IF NOT EXISTS face_person (" +
            "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
            "  person_id VARCHAR(128) COMMENT '人员标识'," +
            "  name VARCHAR(64) NOT NULL COMMENT '姓名'," +
            "  face_id INT DEFAULT 0 COMMENT '人脸ID'," +
            "  quality_score DOUBLE DEFAULT 0 COMMENT '质量分数'," +
            "  device_code VARCHAR(64) COMMENT '设备编号'," +
            "  image_path VARCHAR(255) COMMENT '照片路径'," +
            "  register_source VARCHAR(32) DEFAULT 'MANUAL' COMMENT '注册来源'," +
            "  created_by VARCHAR(64) COMMENT '创建者'," +
            "  status INT DEFAULT 1 COMMENT '状态 1=正常 0=删除'," +
            "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已注册人脸人员'";
        try {
            jdbcTemplate.execute(sql);
            log.info("[DataInitializer] face_person 建表语句执行成功");
        } catch (Exception e) {
            log.error("[DataInitializer] face_person 建表失败: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════
    //  建表：face_record
    // ═══════════════════════════════════════════

    private void createFaceRecordTable() {
        String sql =
            "CREATE TABLE IF NOT EXISTS face_record (" +
            "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
            "  person_id BIGINT COMMENT '人员ID'," +
            "  person_name VARCHAR(128) COMMENT '人员姓名'," +
            "  person_type VARCHAR(32) DEFAULT 'UNKNOWN' COMMENT '人员类型'," +
            "  matched TINYINT DEFAULT 0 COMMENT '是否匹配 1=是 0=否'," +
            "  similarity DOUBLE DEFAULT 0 COMMENT '相似度'," +
            "  threshold_value DOUBLE DEFAULT 0.4 COMMENT '阈值'," +
            "  device_code VARCHAR(64) COMMENT '设备编号'," +
            "  location VARCHAR(128) COMMENT '位置'," +
            "  risk_level VARCHAR(32) DEFAULT 'LOW' COMMENT '风险等级'," +
            "  suggestion VARCHAR(255) COMMENT '建议'," +
            "  alarm_id BIGINT COMMENT '关联告警ID'," +
            "  image_path VARCHAR(255) COMMENT '照片路径'," +
            "  capture_time DATETIME COMMENT '抓拍时间'," +
            "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人脸识别记录'";
        try {
            jdbcTemplate.execute(sql);
            log.info("[DataInitializer] face_record 建表语句执行成功");
        } catch (Exception e) {
            log.error("[DataInitializer] face_record 建表失败: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════
    //  建表：emergency_help_event
    // ═══════════════════════════════════════════

    private void createEmergencyHelpEventTable() {
        String sql =
            "CREATE TABLE IF NOT EXISTS emergency_help_event (" +
            "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
            "  initiator_id BIGINT NOT NULL COMMENT '发起人ID'," +
            "  event_type VARCHAR(32) DEFAULT 'SECURITY' COMMENT '求助类型'," +
            "  urgency VARCHAR(16) DEFAULT 'HIGH' COMMENT '紧急程度'," +
            "  status VARCHAR(32) DEFAULT 'SENT' COMMENT '事件状态'," +
            "  location VARCHAR(255) COMMENT '位置信息'," +
            "  notes TEXT COMMENT '备注'," +
            "  linked_pir_alarm_id BIGINT COMMENT '关联PIR告警'," +
            "  linked_device_code VARCHAR(64) COMMENT '关联设备'," +
            "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
            "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='紧急求助事件'";
        try {
            jdbcTemplate.execute(sql);
            log.info("[DataInitializer] emergency_help_event 建表语句执行成功");
        } catch (Exception e) {
            log.error("[DataInitializer] emergency_help_event 建表失败: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════
    //  建表：emergency_help_receiver
    // ═══════════════════════════════════════════

    private void createEmergencyHelpReceiverTable() {
        String sql =
            "CREATE TABLE IF NOT EXISTS emergency_help_receiver (" +
            "  id BIGINT PRIMARY KEY AUTO_INCREMENT," +
            "  event_id BIGINT NOT NULL COMMENT '事件ID'," +
            "  receiver_id BIGINT NOT NULL COMMENT '接收人ID'," +
            "  receiver_name VARCHAR(64) COMMENT '接收人名称'," +
            "  status VARCHAR(32) DEFAULT 'SENT' COMMENT '接收状态'," +
            "  acknowledged_at DATETIME COMMENT '确认时间'," +
            "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='紧急求助接收人'";
        try {
            jdbcTemplate.execute(sql);
            log.info("[DataInitializer] emergency_help_receiver 建表语句执行成功");
        } catch (Exception e) {
            log.error("[DataInitializer] emergency_help_receiver 建表失败: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════
    //  索引安全创建（先查 information_schema）
    // ═══════════════════════════════════════════

    private void createAllIndexes() {
        // emergency_contact 索引
        safeCreateIndex("emergency_contact", "uk_user_contact",
            "ALTER TABLE emergency_contact ADD UNIQUE INDEX uk_user_contact (user_id, contact_user_id)");
        safeCreateIndex("emergency_contact", "idx_ec_user_id",
            "CREATE INDEX idx_ec_user_id ON emergency_contact (user_id)");
        safeCreateIndex("emergency_contact", "idx_ec_contact_user_id",
            "CREATE INDEX idx_ec_contact_user_id ON emergency_contact (contact_user_id)");

        // user_message 索引
        safeCreateIndex("user_message", "idx_sender_receiver",
            "CREATE INDEX idx_sender_receiver ON user_message (sender_id, receiver_id)");
        safeCreateIndex("user_message", "idx_receiver_read",
            "CREATE INDEX idx_receiver_read ON user_message (receiver_id, read_status)");
        safeCreateIndex("user_message", "idx_um_created_at",
            "CREATE INDEX idx_um_created_at ON user_message (create_time)");

        // face_person 索引
        safeCreateIndex("face_person", "idx_fp_name",
            "CREATE INDEX idx_fp_name ON face_person (name)");
        safeCreateIndex("face_person", "idx_fp_status",
            "CREATE INDEX idx_fp_status ON face_person (status)");

        // face_record 索引
        safeCreateIndex("face_record", "idx_fr_person_id",
            "CREATE INDEX idx_fr_person_id ON face_record (person_id)");
        safeCreateIndex("face_record", "idx_fr_created_at",
            "CREATE INDEX idx_fr_created_at ON face_record (created_at)");

        // emergency_help_event 索引
        safeCreateIndex("emergency_help_event", "idx_ehe_initiator_id",
            "CREATE INDEX idx_ehe_initiator_id ON emergency_help_event (initiator_id)");
        safeCreateIndex("emergency_help_event", "idx_ehe_status",
            "CREATE INDEX idx_ehe_status ON emergency_help_event (status)");

        // emergency_help_receiver 索引
        safeCreateIndex("emergency_help_receiver", "idx_ehr_event_id",
            "CREATE INDEX idx_ehr_event_id ON emergency_help_receiver (event_id)");
        safeCreateIndex("emergency_help_receiver", "idx_ehr_receiver_id",
            "CREATE INDEX idx_ehr_receiver_id ON emergency_help_receiver (receiver_id)");
    }

    private void safeCreateIndex(String tableName, String indexName, String createSql) {
        if (!tableExists(tableName)) {
            log.warn("[DataInitializer] 跳过索引 {}.{}：表不存在", tableName, indexName);
            return;
        }
        if (indexExists(tableName, indexName)) {
            log.info("[DataInitializer] 索引 {}.{} 已存在，跳过", tableName, indexName);
            return;
        }
        // 额外检查：确保涉及的列存在
        // 从 CREATE INDEX / ALTER TABLE ADD INDEX 语句中提取列名做基本检查
        String upperSql = createSql.toUpperCase();
        if (upperSql.contains("(") && upperSql.contains(")")) {
            String colsPart = upperSql.substring(upperSql.indexOf("(") + 1, upperSql.lastIndexOf(")"));
            String[] cols = colsPart.split(",");
            for (String col : cols) {
                String colName = col.trim().split("\\s+")[0];
                if (!columnExists(tableName, colName)) {
                    log.warn("[DataInitializer] 跳过索引 {}.{}：列 {}.{} 不存在", tableName, indexName, tableName, colName);
                    return;
                }
            }
        }
        try {
            jdbcTemplate.execute(createSql);
            log.info("[DataInitializer] 索引 {}.{} 创建成功", tableName, indexName);
        } catch (Exception e) {
            log.warn("[DataInitializer] 索引 {}.{} 创建失败: {}", tableName, indexName, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════
    //  安全补列
    // ═══════════════════════════════════════════

    private boolean columnExists(String tableName, String columnName) {
        try {
            if (!tableExists(tableName)) return false;
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() " +
                "AND table_name = ? AND column_name = ?",
                Integer.class, tableName, columnName
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("[DataInitializer] 检查列 {}.{} 失败: {}", tableName, columnName, e.getMessage());
            return false;
        }
    }

    private void safeAddColumn(String tableName, String columnName, String columnDef) {
        if (!tableExists(tableName)) {
            log.warn("[DataInitializer] 跳过补列 {}.{}：表不存在", tableName, columnName);
            return;
        }
        if (columnExists(tableName, columnName)) {
            log.info("[DataInitializer] 列 {}.{} 已存在，跳过", tableName, columnName);
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDef);
            log.info("[DataInitializer] 补列 {}.{} ({}) 成功", tableName, columnName, columnDef);
        } catch (Exception e) {
            log.warn("[DataInitializer] 补列 {}.{} 失败: {}", tableName, columnName, e.getMessage());
        }
    }

    private void ensureAllMissingColumns() {
        // face_person — 旧表可能缺少这些列
        safeAddColumn("face_person", "image_path", "VARCHAR(255) COMMENT '照片路径'");
        safeAddColumn("face_person", "register_source", "VARCHAR(32) DEFAULT 'MANUAL' COMMENT '注册来源'");
        safeAddColumn("face_person", "created_by", "VARCHAR(64) COMMENT '创建者'");
        safeAddColumn("face_person", "status", "INT DEFAULT 1 COMMENT '状态 1=正常 0=删除'");
        safeAddColumn("face_person", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");

        // face_record — 旧表可能缺少这些列
        safeAddColumn("face_record", "person_id", "BIGINT COMMENT '人员ID'");
        safeAddColumn("face_record", "image_path", "VARCHAR(255) COMMENT '照片路径'");

        // user_message — 补充缺失列
        safeAddColumn("user_message", "title", "VARCHAR(200) COMMENT '消息标题'");
        safeAddColumn("user_message", "read_time", "DATETIME");
        safeAddColumn("user_message", "deleted_by_sender", "INT DEFAULT 0");
        safeAddColumn("user_message", "deleted_by_receiver", "INT DEFAULT 0");
        safeAddColumn("user_message", "is_emergency", "INT DEFAULT 0 COMMENT '是否紧急'");

        // emergency_contact — 补充缺失列
        safeAddColumn("emergency_contact", "username", "VARCHAR(64) COMMENT '用户名'");
    }

    // ═══════════════════════════════════════════
    //  清理 emergency_contact 重复记录
    // ═══════════════════════════════════════════

    private void cleanDuplicateContacts() {
        try {
            String sql =
                "DELETE e1 FROM emergency_contact e1 " +
                "INNER JOIN emergency_contact e2 " +
                "WHERE e1.id > e2.id " +
                "AND e1.user_id = e2.user_id " +
                "AND e1.contact_user_id = e2.contact_user_id " +
                "AND e1.status = 1 AND e2.status = 1";
            int deleted = jdbcTemplate.update(sql);
            if (deleted > 0) {
                log.info("[DataInitializer] 清理了 {} 条重复紧急联系人记录", deleted);
            }
        } catch (Exception e) {
            log.debug("[DataInitializer] 无重复紧急联系人需清理: {}", e.getMessage());
        }
    }
}
