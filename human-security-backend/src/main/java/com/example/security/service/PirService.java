package com.example.security.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.security.entity.*;
import com.example.security.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * PIR 人体感应监测服务
 * 处理 PIR 设备状态查询、记录、统计、布防/撤防等业务逻辑
 */
@Service
public class PirService {

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private HumanRecordMapper humanRecordMapper;

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    @Autowired
    private DeviceStatusMapper deviceStatusMapper;

    @Autowired
    private CommandRecordMapper commandRecordMapper;

    @Autowired
    private MqttPublisherService mqttPublisherService;

    /**
     * 获取所有 PIR 设备实时状态
     */
    public List<Map<String, Object>> getPirDeviceStatus() {
        List<Map<String, Object>> result = new ArrayList<>();

        // 从 device 表查询
        List<Device> devices = deviceMapper.selectList(
                new QueryWrapper<Device>().orderByAsc("id"));
        for (Device d : devices) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", d.getId());
            item.put("deviceCode", d.getDeviceCode());
            item.put("deviceName", d.getDeviceName());
            item.put("location", d.getLocation());
            item.put("onlineStatus", d.getOnlineStatus() != null ? d.getOnlineStatus() : 0);
            item.put("humanDetected", d.getHumanDetected() != null ? d.getHumanDetected() : 0);
            item.put("defenseStatus", d.getDefenseStatus() != null ? d.getDefenseStatus() : 0);
            item.put("lastReportTime", d.getLastReportTime() != null
                    ? d.getLastReportTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
            item.put("lastOnline", d.getLastOnline() != null
                    ? d.getLastOnline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
            result.add(item);
        }

        // 也从 device_status 表补充
        List<DeviceStatus> statuses = deviceStatusMapper.selectList(null);
        Set<String> seenCodes = new HashSet<>();
        for (Map<String, Object> item : result) {
            seenCodes.add((String) item.get("deviceCode"));
        }
        for (DeviceStatus ds : statuses) {
            if (!seenCodes.contains(ds.getDeviceCode())) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", ds.getId());
                item.put("deviceCode", ds.getDeviceCode());
                item.put("deviceName", ds.getDeviceName());
                item.put("location", ds.getLocation());
                item.put("onlineStatus", ds.getOnline() != null ? ds.getOnline() : 0);
                item.put("humanDetected", 0);
                item.put("defenseStatus", ds.getDefense() != null ? ds.getDefense() : 0);
                item.put("lastReportTime", ds.getLastReportTime() != null
                        ? ds.getLastReportTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
                item.put("lastOnline", ds.getUpdatedAt() != null
                        ? ds.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
                result.add(item);
            }
        }

        return result;
    }

    /**
     * 获取 PIR 感应记录（分页）
     */
    public Map<String, Object> getPirRecords(Integer page, Integer pageSize,
                                              String deviceCode, String startTime, String endTime) {
        QueryWrapper<HumanRecord> wrapper = new QueryWrapper<HumanRecord>()
                .orderByDesc("id");

        if (deviceCode != null && !deviceCode.isEmpty()) {
            wrapper.eq("device_code", deviceCode);
        }
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge("report_time", startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le("report_time", endTime);
        }

        long total = humanRecordMapper.selectCount(wrapper);
        int offset = (page - 1) * pageSize;
        List<HumanRecord> records = humanRecordMapper.selectList(
                wrapper.last("LIMIT " + offset + "," + pageSize));

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", records);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 手动上报 PIR 数据
     */
    public boolean reportPirData(String deviceCode, Integer humanDetected, Integer defenseStatus,
                                  Map<String, Object> rawData) {
        if (deviceCode == null || deviceCode.isEmpty()) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        // 保存人体感应记录
        HumanRecord record = new HumanRecord();
        record.setDeviceCode(deviceCode);
        record.setHumanDetected(humanDetected);
        record.setDefenseStatus(defenseStatus);
        record.setReportTime(now);
        humanRecordMapper.insert(record);

        // 更新设备状态
        Device device = deviceMapper.selectOne(
                new QueryWrapper<Device>().eq("device_code", deviceCode));
        if (device != null) {
            device.setOnlineStatus(1);
            device.setHumanDetected(humanDetected);
            device.setDefenseStatus(defenseStatus);
            device.setLastReportTime(now);
            device.setLastOnline(now);
            deviceMapper.updateById(device);
        }

        // 布防 + 检测到人体 → 生成告警（5分钟内去重）
        if (humanDetected == 1 && defenseStatus == 1) {
            boolean alreadyAlarmed = alarmRecordMapper.selectCount(
                    new QueryWrapper<AlarmRecord>()
                            .eq("device_code", deviceCode)
                            .eq("handle_status", 0)
                            .ge("create_time", now.minusMinutes(5))
            ) > 0;

            if (!alreadyAlarmed) {
                AlarmRecord alarm = new AlarmRecord();
                alarm.setDeviceCode(deviceCode);
                alarm.setDeviceName(device != null ? device.getDeviceName() : deviceCode);
                alarm.setLocation(device != null ? device.getLocation() : null);
                alarm.setAlarmType("PIR_HUMAN_DETECTED");
                alarm.setAlarmLevel("HIGH");
                alarm.setAlarmContent("布防状态下检测到人体活动" + " [" + now.format(
                        java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")) + "]");
                alarm.setAlarmStatus("UNHANDLED");
                alarm.setAlarmTime(now);
                alarm.setCreateTime(now);
                alarmRecordMapper.insert(alarm);

                // 回填告警ID到记录
                record.setIsAlarm(1);
                record.setAlarmId(alarm.getId());
                humanRecordMapper.updateById(record);

                System.out.println("PIR 告警生成: device=" + deviceCode + ", alarmId=" + alarm.getId());
            }
        }

        return true;
    }

    /**
     * PIR 统计数据
     */
    public Map<String, Object> getPirStats() {
        Map<String, Object> stats = new HashMap<>();

        // 设备统计
        Long pirDeviceTotal = deviceMapper.selectCount(null);
        Long pirDeviceOnline = deviceMapper.selectCount(
                new QueryWrapper<Device>().eq("online_status", 1));
        Long pirDeviceArmed = deviceMapper.selectCount(
                new QueryWrapper<Device>().eq("defense_status", 1));

        stats.put("pirDeviceTotal", pirDeviceTotal);
        stats.put("pirDeviceOnline", pirDeviceOnline);
        stats.put("pirDeviceArmed", pirDeviceArmed);

        // 今日感应次数
        long todayCount = getTodayDetectionCount();
        stats.put("todayDetectionCount", todayCount);

        // 今日告警次数
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        long todayAlarmCount = alarmRecordMapper.selectCount(
                new QueryWrapper<AlarmRecord>()
                        .ge("create_time", todayStart)
                        .le("create_time", todayEnd));
        stats.put("todayAlarmCount", todayAlarmCount);

        // 最近一次感应
        Map<String, Object> latest = getLatestDetection();
        stats.put("latestDetection", latest);

        return stats;
    }

    /**
     * 今日人体感应次数
     */
    public long getTodayDetectionCount() {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        return humanRecordMapper.selectCount(
                new QueryWrapper<HumanRecord>()
                        .ge("report_time", todayStart)
                        .le("report_time", todayEnd));
    }

    /**
     * 最近一次感应
     */
    public Map<String, Object> getLatestDetection() {
        HumanRecord latest = humanRecordMapper.selectOne(
                new QueryWrapper<HumanRecord>()
                        .orderByDesc("id")
                        .last("LIMIT 1"));
        Map<String, Object> result = new HashMap<>();
        if (latest != null) {
            result.put("deviceCode", latest.getDeviceCode());
            result.put("humanDetected", latest.getHumanDetected());
            result.put("defenseStatus", latest.getDefenseStatus());
            result.put("reportTime", latest.getReportTime() != null
                    ? latest.getReportTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
            result.put("isAlarm", latest.getIsAlarm());
        }
        return result;
    }

    /**
     * PIR 布防 — 向 topic b 发布 "1"，同时兼容旧通道
     */
    public boolean armDevice(String deviceCode, String operator) {
        boolean sent = mqttPublisherService.publishCommand("1");
        // 兼容旧通道
        mqttPublisherService.publishToTopic(
                "human/device/" + deviceCode + "/command",
                "{\"command\":\"DEFENSE_ON\"}");

        // 记录指令
        CommandRecord cr = new CommandRecord();
        cr.setDeviceCode(deviceCode);
        cr.setCommand("DEFENSE_ON");
        cr.setDirection("SEND");
        cr.setStatus(sent ? "PENDING" : "FAILED");
        cr.setSendTime(LocalDateTime.now());
        cr.setCreateTime(LocalDateTime.now());
        commandRecordMapper.insert(cr);

        // 更新设备状态
        Device device = deviceMapper.selectOne(
                new QueryWrapper<Device>().eq("device_code", deviceCode));
        if (device != null) {
            device.setDefenseStatus(1);
            deviceMapper.updateById(device);
        }

        return sent;
    }

    /**
     * PIR 撤防 — 向 topic b 发布 "0"，同时兼容旧通道
     */
    public boolean disarmDevice(String deviceCode, String operator) {
        boolean sent = mqttPublisherService.publishCommand("0");
        // 兼容旧通道
        mqttPublisherService.publishToTopic(
                "human/device/" + deviceCode + "/command",
                "{\"command\":\"DEFENSE_OFF\"}");

        // 记录指令
        CommandRecord cr = new CommandRecord();
        cr.setDeviceCode(deviceCode);
        cr.setCommand("DEFENSE_OFF");
        cr.setDirection("SEND");
        cr.setStatus(sent ? "PENDING" : "FAILED");
        cr.setSendTime(LocalDateTime.now());
        cr.setCreateTime(LocalDateTime.now());
        commandRecordMapper.insert(cr);

        // 更新设备状态
        Device device = deviceMapper.selectOne(
                new QueryWrapper<Device>().eq("device_code", deviceCode));
        if (device != null) {
            device.setDefenseStatus(0);
            deviceMapper.updateById(device);
        }

        return sent;
    }
}
