package com.example.security.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.security.dto.HumanDataDTO;
import com.example.security.entity.*;
import com.example.security.mapper.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MqttMessageService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 设备上次写入 human_record 的时间（用于降频）
    private final Map<String, LocalDateTime> lastRecordWriteTime = new ConcurrentHashMap<>();

    // 设备上次告警时间（用于去重，30秒内同设备同类型只告警一次）
    private final Map<String, LocalDateTime> lastAlarmTime = new ConcurrentHashMap<>();
    private static final int ALARM_DEDUP_SECONDS = 30;
    private static final int RECORD_THROTTLE_SECONDS = 3;

    @Autowired
    private HumanRecordMapper humanRecordMapper;

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private CommandRecordMapper commandRecordMapper;

    @Autowired(required = false)
    private HuaweiIotdaForwardService huaweiIotdaForwardService;

    public void handleMessage(String topic, String payload) {
        System.out.println("收到 Topic：" + topic);
        System.out.println("收到消息：" + payload);

        try {
            // Topic "a" — 设备上报主通道
            if ("a".equals(topic)) {
                handleTopicA(payload);
                return;
            }
            // 旧兼容 Topic
            if (topic.contains("/data")) {
                handleDataMessage(topic, payload);
            } else if (topic.contains("/alarm")) {
                handlePirAlarmMessage(topic, payload);
            } else if (topic.contains("/reply")) {
                handleReplyMessage(topic, payload);
            } else if (topic.contains("/status")) {
                handleStatusMessage(topic, payload);
            }
        } catch (Exception e) {
            System.out.println("MQTT 消息处理失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理 topic "a" 上报数据
     * 设备上报 JSON:
     * {
     *   "device_code": "device_001",
     *   "human_detected": 1,
     *   "defense_status": 1
     * }
     */
    private void handleTopicA(String payload) {
        try {
            JsonNode data = objectMapper.readTree(payload);
            String deviceCode = data.has("device_code") ? data.get("device_code").asText() : null;
            if (deviceCode == null || deviceCode.isEmpty()) {
                System.out.println("topic a 消息缺少 device_code，忽略");
                return;
            }

            int humanDetected = data.has("human_detected") ? data.get("human_detected").asInt() : 0;
            int defenseStatus = data.has("defense_status") ? data.get("defense_status").asInt() : 0;

            System.out.println("topic a → deviceCode=" + deviceCode
                    + ", humanDetected=" + humanDetected
                    + ", defenseStatus=" + defenseStatus);

            // 转换为 HumanDataDTO 复用处理逻辑
            HumanDataDTO dto = new HumanDataDTO();
            dto.setDeviceCode(deviceCode);
            dto.setHumanDetected(humanDetected);
            dto.setDefenseStatus(defenseStatus);

            processHumanData(dto);
        } catch (Exception e) {
            System.out.println("topic a 解析失败：" + e.getMessage());
        }
    }

    private void handleDataMessage(String topic, String payload) throws Exception {
        HumanDataDTO data = objectMapper.readValue(payload, HumanDataDTO.class);
        processHumanData(data);
    }

    /**
     * 处理人体感应数据（供 MQTT 和华为云 IoTDA 共用）
     */
    public void processHumanData(HumanDataDTO data) {
        String deviceCode = data.getDeviceCode();
        if (deviceCode == null || deviceCode.isEmpty()) return;

        System.out.println("处理人体感应数据: deviceCode=" + deviceCode
                + ", humanDetected=" + data.getHumanDetected()
                + ", defenseStatus=" + data.getDefenseStatus());

        LocalDateTime now = LocalDateTime.now();
        boolean shouldWriteRecord = false;
        boolean isAlarmTriggered = false;

        // 更新 device 表（每次都更新）
        Device device = deviceMapper.selectOne(
                new QueryWrapper<Device>().eq("device_code", deviceCode));
        if (device != null) {
            device.setOnlineStatus(1);
            device.setHumanDetected(data.getHumanDetected());
            device.setDefenseStatus(data.getDefenseStatus());
            device.setLastReportTime(now);
            device.setLastOnline(now);
            device.setUpdateTime(now);
            deviceMapper.updateById(device);
        } else {
            // 设备不存在，自动创建
            device = new Device();
            device.setDeviceCode(deviceCode);
            device.setDeviceName(deviceCode);
            device.setOnlineStatus(1);
            device.setHumanDetected(data.getHumanDetected());
            device.setDefenseStatus(data.getDefenseStatus());
            device.setLastReportTime(now);
            device.setLastOnline(now);
            device.setCreateTime(now);
            device.setUpdateTime(now);
            deviceMapper.insert(device);
            System.out.println("自动创建设备: " + deviceCode);
        }

        // 判断是否需要写入 human_record（降频）
        LocalDateTime lastWrite = lastRecordWriteTime.get(deviceCode);
        Integer prevHumanDetected = device.getHumanDetected();
        Integer prevDefenseStatus = device.getDefenseStatus();

        boolean stateChanged = !data.getHumanDetected().equals(prevHumanDetected)
                || !data.getDefenseStatus().equals(prevDefenseStatus);
        boolean throttleExpired = lastWrite == null
                || java.time.Duration.between(lastWrite, now).getSeconds() >= RECORD_THROTTLE_SECONDS;

        // 布防状态 + 检测到人体 → 检查是否触发告警
        if (data.getHumanDetected() != null && data.getHumanDetected() == 1
                && data.getDefenseStatus() != null && data.getDefenseStatus() == 1) {
            shouldWriteRecord = true; // 触发告警时必须写入
            isAlarmTriggered = true;
        } else if (stateChanged || throttleExpired) {
            shouldWriteRecord = true;
        }

        if (shouldWriteRecord) {
            HumanRecord record = new HumanRecord();
            record.setDeviceCode(deviceCode);
            record.setHumanDetected(data.getHumanDetected());
            record.setDefenseStatus(data.getDefenseStatus());
            record.setReportTime(now);
            record.setIsAlarm(0);

            // 保存 raw_data
            try {
                record.setRawData(objectMapper.writeValueAsString(data));
            } catch (Exception ignored) {}

            humanRecordMapper.insert(record);
            lastRecordWriteTime.put(deviceCode, now);
            System.out.println("human_record 写入: deviceCode=" + deviceCode
                    + (stateChanged ? " (状态变化)" : "")
                    + (isAlarmTriggered ? " (触发告警)" : " (降频写入)"));
        } else {
            System.out.println("human_record 跳过写入（未达降频阈值）: deviceCode=" + deviceCode);
        }

        // 告警生成：布防 + 检测到人体 → 生成 INTRUSION 告警（30秒去重）
        if (isAlarmTriggered) {
            LocalDateTime lastAlarm = lastAlarmTime.get(deviceCode);
            boolean dedupExpired = lastAlarm == null
                    || java.time.Duration.between(lastAlarm, now).getSeconds() >= ALARM_DEDUP_SECONDS;

            // 同时检查数据库去重
            boolean alreadyAlarmed = alarmRecordMapper.selectCount(
                    new QueryWrapper<AlarmRecord>()
                            .eq("device_code", deviceCode)
                            .eq("alarm_type", "INTRUSION")
                            .eq("handle_status", 0)
                            .ge("create_time", now.minusSeconds(ALARM_DEDUP_SECONDS))
            ) > 0;

            if (dedupExpired && !alreadyAlarmed) {
                AlarmRecord alarm = new AlarmRecord();
                alarm.setDeviceCode(deviceCode);
                alarm.setDeviceName(device.getDeviceName() != null ? device.getDeviceName() : deviceCode);
                alarm.setLocation(device.getLocation() != null ? device.getLocation() : null);
                alarm.setAlarmType("INTRUSION");
                alarm.setAlarmLevel("HIGH");
                alarm.setAlarmContent((device.getDeviceName() != null
                        ? device.getDeviceName() + "（" + deviceCode + "）在布防状态下检测到人体活动，触发入侵告警"
                        : "设备 " + deviceCode + " 在布防状态下检测到人体活动，触发入侵告警")
                        + " [" + now.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")) + "]");
                alarm.setAlarmStatus("UNHANDLED");
                alarm.setAlarmTime(now);
                alarm.setCreateTime(now);

                alarmRecordMapper.insert(alarm);
                lastAlarmTime.put(deviceCode, now);
                System.out.println("已生成入侵告警: alarmId=" + alarm.getId()
                        + ", deviceCode=" + deviceCode);

                // 回填告警ID到人体感应记录
                HumanRecord latestRecord = humanRecordMapper.selectOne(
                        new QueryWrapper<HumanRecord>()
                                .eq("device_code", deviceCode)
                                .orderByDesc("id")
                                .last("LIMIT 1"));
                if (latestRecord != null) {
                    latestRecord.setIsAlarm(1);
                    latestRecord.setAlarmId(alarm.getId());
                    humanRecordMapper.updateById(latestRecord);
                }
            } else {
                System.out.println("入侵告警去重跳过: deviceCode=" + deviceCode);
            }
        }

        // 华为云 IoTDA 同步转发（可选）
        if (huaweiIotdaForwardService != null) {
            try {
                huaweiIotdaForwardService.forward(data);
            } catch (Exception e) {
                System.out.println("华为云 IoTDA 转发异常: " + e.getMessage());
            }
        }
    }

    /**
     * 处理 PIR 告警消息（旧兼容）
     */
    private void handlePirAlarmMessage(String topic, String payload) {
        System.out.println("收到 PIR 告警消息：" + payload);
        try {
            JsonNode alarmData = objectMapper.readTree(payload);
            String deviceCode = alarmData.has("device_code")
                    ? alarmData.get("device_code").asText()
                    : extractDeviceCode(topic);

            LocalDateTime now = LocalDateTime.now();
            boolean alreadyAlarmed = alarmRecordMapper.selectCount(
                    new QueryWrapper<AlarmRecord>()
                            .eq("device_code", deviceCode)
                            .eq("alarm_type", "INTRUSION")
                            .eq("handle_status", 0)
                            .ge("create_time", now.minusSeconds(ALARM_DEDUP_SECONDS))
            ) > 0;

            if (alreadyAlarmed) {
                System.out.println("PIR 告警去重跳过: deviceCode=" + deviceCode);
                return;
            }

            AlarmRecord alarm = new AlarmRecord();
            alarm.setDeviceCode(deviceCode);
            alarm.setAlarmType("INTRUSION");
            alarm.setAlarmLevel("HIGH");
            alarm.setAlarmContent((alarmData.has("content")
                    ? alarmData.get("content").asText()
                    : "PIR 设备检测到人体活动告警")
                    + " [" + now.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")) + "]");
            alarm.setAlarmStatus("UNHANDLED");
            alarm.setAlarmTime(now);
            alarm.setCreateTime(now);

            alarmRecordMapper.insert(alarm);
            System.out.println("PIR 告警已生成: device=" + deviceCode + ", alarmId=" + alarm.getId());
        } catch (Exception e) {
            System.out.println("PIR 告警处理失败：" + e.getMessage());
        }
    }

    /**
     * 处理设备状态消息（旧兼容）
     */
    private void handleStatusMessage(String topic, String payload) {
        System.out.println("收到状态更新：" + payload);
        try {
            String deviceCode = extractDeviceCode(topic);
            if (deviceCode == null) return;

            Device device = deviceMapper.selectOne(
                    new QueryWrapper<Device>().eq("device_code", deviceCode));
            if (device != null) {
                device.setOnlineStatus(1);
                device.setLastOnline(LocalDateTime.now());
                device.setUpdateTime(LocalDateTime.now());

                try {
                    JsonNode statusData = objectMapper.readTree(payload);
                    if (statusData.has("online_status")) {
                        device.setOnlineStatus(statusData.get("online_status").asInt());
                    }
                    if (statusData.has("human_detected")) {
                        device.setHumanDetected(statusData.get("human_detected").asInt());
                    }
                    if (statusData.has("defense_status")) {
                        device.setDefenseStatus(statusData.get("defense_status").asInt());
                    }
                } catch (Exception ignored) {}

                deviceMapper.updateById(device);
                System.out.println("设备状态已更新：" + deviceCode);
            }
        } catch (Exception e) {
            System.out.println("状态更新处理失败：" + e.getMessage());
        }
    }

    private void handleReplyMessage(String topic, String payload) throws Exception {
        System.out.println("收到设备回复：" + payload);
        String deviceCode = extractDeviceCode(topic);
        if (deviceCode == null) return;

        JsonNode reply = objectMapper.readTree(payload);

        CommandRecord cr = commandRecordMapper.selectOne(
                new QueryWrapper<CommandRecord>()
                        .eq("device_code", deviceCode)
                        .orderByDesc("id")
                        .last("LIMIT 1"));

        if (cr != null && "PENDING".equals(cr.getStatus())) {
            String replyStatus = reply.has("status") ? reply.get("status").asText() : "SUCCESS";
            if (reply.has("result") && !reply.get("result").asBoolean()) {
                replyStatus = "FAILED";
            }
            cr.setStatus(replyStatus);
            cr.setReplyContent(payload);
            commandRecordMapper.updateById(cr);
            System.out.println("指令记录已更新：" + cr.getCommand() + " → " + replyStatus);
        }

        CommandRecord replyRecord = new CommandRecord();
        replyRecord.setDeviceCode(deviceCode);
        replyRecord.setCommand(reply.has("command") ? reply.get("command").asText() : "REPLY");
        replyRecord.setDirection("REPLY");
        replyRecord.setStatus("SUCCESS");
        replyRecord.setReplyContent(payload);
        replyRecord.setCreateTime(LocalDateTime.now());
        commandRecordMapper.insert(replyRecord);

        Device device = deviceMapper.selectOne(
                new QueryWrapper<Device>().eq("device_code", deviceCode));
        if (device != null) {
            device.setLastOnline(LocalDateTime.now());
            device.setUpdateTime(LocalDateTime.now());
            if (reply.has("defenseStatus")) {
                device.setDefenseStatus(reply.get("defenseStatus").asInt());
            }
            deviceMapper.updateById(device);
        }
    }

    private String extractDeviceCode(String topic) {
        String[] parts = topic.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        return null;
    }
}
