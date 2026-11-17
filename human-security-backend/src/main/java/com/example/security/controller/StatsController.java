package com.example.security.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.security.entity.*;
import com.example.security.mapper.*;
import com.example.security.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api")
public class StatsController {

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private HumanRecordMapper humanRecordMapper;

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    @Autowired
    private DeviceService deviceService;

    /**
     * GET /api/stats — 兼容旧接口
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = buildStats();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", stats);
        return result;
    }

    /**
     * GET /api/statistics/overview — 首页统计概览
     */
    @GetMapping("/statistics/overview")
    public Map<String, Object> getOverview(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false, defaultValue = "admin") String role) {
        Map<String, Object> stats = buildStats(userId, role);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("success", true);
        result.put("message", "查询成功");
        result.put("data", stats);
        return result;
    }

    /**
     * GET /api/statistics/realtime-overview — 实时统计概览（新前端字段名）
     */
    @GetMapping("/statistics/realtime-overview")
    public Map<String, Object> getRealtimeOverview() {
        Map<String, Object> stats = buildRealtimeStats();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("success", true);
        result.put("message", "查询成功");
        result.put("data", stats);
        return result;
    }

    /**
     * GET /api/statistics/alarm-trend?days=7&userId=&role=
     * 告警趋势数据（按角色过滤）
     */
    @GetMapping("/statistics/alarm-trend")
    public Map<String, Object> getAlarmTrend(
            @RequestParam(defaultValue = "7") Integer days,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false, defaultValue = "admin") String role) {
        Map<String, Object> result = new HashMap<>();

        Map<String, Integer> trend = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            trend.put(d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), 0);
        }

        LocalDateTime startTime = LocalDateTime.of(today.minusDays(days - 1), LocalTime.MIN);
        QueryWrapper<AlarmRecord> qw = new QueryWrapper<AlarmRecord>()
                .ge("alarm_time", startTime);

        // 按角色过滤：只看用户有权限的设备的告警
        if (userId != null && !"admin".equals(role)) {
            List<String> codes = deviceService.getVisibleDeviceCodes(userId, role);
            if (codes.isEmpty()) {
                qw.eq("device_code", "__none__"); // 无可见设备
            } else {
                qw.in("device_code", codes);
            }
        }

        List<AlarmRecord> alarms = alarmRecordMapper.selectList(qw);
        for (AlarmRecord a : alarms) {
            if (a.getAlarmTime() != null) {
                String key = a.getAlarmTime().toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                if (trend.containsKey(key)) {
                    trend.put(key, trend.get(key) + 1);
                }
            }
        }

        List<Map<String, Object>> trendList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : trend.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", entry.getKey());
            item.put("count", entry.getValue());
            trendList.add(item);
        }

        result.put("code", 200);
        result.put("success", true);
        result.put("message", "查询成功");
        result.put("data", trendList);
        return result;
    }

    /**
     * buildStats — 兼容旧前端字段名
     */
    private Map<String, Object> buildStats() {
        return buildStats(null, "admin");
    }

    private Map<String, Object> buildStats(Long userId, String role) {
        Map<String, Object> stats = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = LocalDateTime.of(today, LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(today, LocalTime.MAX);

        // 获取该用户有权限的设备编号列表
        List<String> visibleCodes = (userId != null && !"admin".equals(role))
                ? deviceService.getVisibleDeviceCodes(userId, role) : null;
        boolean filterByDevice = visibleCodes != null;

        // 设备统计（来自 device 表）— 按角色过滤
        QueryWrapper<Device> deviceQw = new QueryWrapper<>();
        if (filterByDevice) {
            if (visibleCodes.isEmpty()) {
                deviceQw.eq("device_code", "__none__");
            } else {
                deviceQw.in("device_code", visibleCodes);
            }
        }
        Long totalDevices = deviceMapper.selectCount(deviceQw);
        QueryWrapper<Device> onlineQw = new QueryWrapper<Device>().eq("online_status", 1);
        if (filterByDevice) {
            if (visibleCodes.isEmpty()) { onlineQw.eq("device_code", "__none__"); }
            else { onlineQw.in("device_code", visibleCodes); }
        }
        Long onlineDevices = deviceMapper.selectCount(onlineQw);
        Long offlineDevices = totalDevices - onlineDevices;
        QueryWrapper<Device> armedQw = new QueryWrapper<Device>().eq("defense_status", 1);
        QueryWrapper<Device> detectedDevQw = new QueryWrapper<Device>().eq("human_detected", 1);
        if (filterByDevice) {
            if (visibleCodes.isEmpty()) {
                armedQw.eq("device_code", "__none__");
                detectedDevQw.eq("device_code", "__none__");
            } else {
                armedQw.in("device_code", visibleCodes);
                detectedDevQw.in("device_code", visibleCodes);
            }
        }
        Long armedDevices = deviceMapper.selectCount(armedQw);
        Long disarmedDevices = totalDevices - armedDevices;
        Long humanDetectedDevices = deviceMapper.selectCount(detectedDevQw);

        // 设备在线率
        double onlineRate = totalDevices > 0
                ? Math.round(onlineDevices * 1000.0 / totalDevices) / 10.0
                : 0.0;

        // 告警统计（来自 alarm_record 表）— 按设备过滤
        QueryWrapper<AlarmRecord> todayAlarmQw = new QueryWrapper<AlarmRecord>()
                .ge("alarm_time", todayStart).le("alarm_time", todayEnd);
        QueryWrapper<AlarmRecord> unhandledQw = new QueryWrapper<AlarmRecord>().eq("handle_status", 0);
        QueryWrapper<AlarmRecord> handledQw = new QueryWrapper<AlarmRecord>().eq("handle_status", 1);
        QueryWrapper<AlarmRecord> misreportQw = new QueryWrapper<AlarmRecord>().eq("handle_status", 2);
        QueryWrapper<AlarmRecord> highQw = new QueryWrapper<AlarmRecord>().eq("alarm_level", "HIGH");
        if (filterByDevice && !visibleCodes.isEmpty()) {
            todayAlarmQw.in("device_code", visibleCodes);
            unhandledQw.in("device_code", visibleCodes);
            handledQw.in("device_code", visibleCodes);
            misreportQw.in("device_code", visibleCodes);
            highQw.in("device_code", visibleCodes);
        } else if (filterByDevice) {
            todayAlarmQw.eq("device_code", "__none__");
            unhandledQw.eq("device_code", "__none__");
            handledQw.eq("device_code", "__none__");
            misreportQw.eq("device_code", "__none__");
            highQw.eq("device_code", "__none__");
        }
        Long todayAlarms = alarmRecordMapper.selectCount(todayAlarmQw);
        Long unhandledAlarms = alarmRecordMapper.selectCount(unhandledQw);
        Long handledAlarms = alarmRecordMapper.selectCount(handledQw);
        Long misreportAlarms = alarmRecordMapper.selectCount(misreportQw);
        Long highLevelAlarms = alarmRecordMapper.selectCount(highQw);

        // 人体感应统计 — 按设备过滤
        QueryWrapper<HumanRecord> todayRecQw = new QueryWrapper<HumanRecord>()
                .ge("report_time", todayStart).le("report_time", todayEnd);
        QueryWrapper<HumanRecord> detectedQw = new QueryWrapper<HumanRecord>()
                .eq("human_detected", 1).ge("report_time", todayStart).le("report_time", todayEnd);
        QueryWrapper<HumanRecord> latestQw = new QueryWrapper<HumanRecord>().orderByDesc("report_time").last("LIMIT 1");
        if (filterByDevice && !visibleCodes.isEmpty()) {
            todayRecQw.in("device_code", visibleCodes);
            detectedQw.in("device_code", visibleCodes);
            latestQw.in("device_code", visibleCodes);
        } else if (filterByDevice) {
            todayRecQw.eq("device_code", "__none__");
            detectedQw.eq("device_code", "__none__");
            latestQw.eq("device_code", "__none__");
        }
        Long todayRecords = humanRecordMapper.selectCount(todayRecQw);
        long humanDetectedCount = humanRecordMapper.selectCount(detectedQw);
        Long totalRecords = humanRecordMapper.selectCount(filterByDevice && !visibleCodes.isEmpty()
                ? new QueryWrapper<HumanRecord>().in("device_code", visibleCodes) : new QueryWrapper<>());

        // 最近人体感应状态
        HumanRecord latestHuman = humanRecordMapper.selectOne(latestQw);
        int latestHumanDetected = (latestHuman != null && latestHuman.getHumanDetected() != null)
                ? latestHuman.getHumanDetected() : 0;

        // 兼容旧字段名
        stats.put("totalDevices", totalDevices);
        stats.put("onlineDevices", onlineDevices);
        stats.put("offlineDevices", offlineDevices);
        stats.put("armedDevices", armedDevices);
        stats.put("disarmedDevices", disarmedDevices);
        stats.put("humanDetectedDevices", humanDetectedDevices);
        stats.put("todayAlarms", todayAlarms);
        stats.put("unhandledAlarms", unhandledAlarms);
        stats.put("handledAlarms", handledAlarms);
        stats.put("misreportAlarms", misreportAlarms);
        stats.put("highLevelAlarms", highLevelAlarms);
        stats.put("todayRecords", todayRecords);
        stats.put("humanDetectedCount", humanDetectedCount);
        stats.put("totalRecords", totalRecords);
        stats.put("lastUpdateTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 新字段名（前端 Dashboard.vue 使用）
        stats.put("deviceTotal", totalDevices);
        stats.put("deviceOnline", onlineDevices);
        stats.put("deviceOffline", offlineDevices);
        stats.put("deviceOnlineRate", onlineRate);
        stats.put("todayAlarmCount", todayAlarms);
        stats.put("unhandledAlarmCount", unhandledAlarms);
        stats.put("handledAlarmCount", handledAlarms);
        stats.put("falseAlarmCount", misreportAlarms);
        stats.put("highAlarmCount", highLevelAlarms);
        stats.put("todayHumanCount", todayRecords);
        stats.put("latestHumanDetected", latestHumanDetected);
        stats.put("defenseDeviceCount", armedDevices);

        // 设备分布
        Map<String, Long> defenseDist = new HashMap<>();
        defenseDist.put("armed", armedDevices);
        defenseDist.put("disarmed", disarmedDevices);
        stats.put("defenseDistribution", defenseDist);

        Map<String, Long> onlineDist = new HashMap<>();
        onlineDist.put("online", onlineDevices);
        onlineDist.put("offline", offlineDevices);
        stats.put("onlineDistribution", onlineDist);

        return stats;
    }

    /**
     * buildRealtimeStats — 新前端字段名（与 realtime-overview 对应）
     */
    private Map<String, Object> buildRealtimeStats() {
        Map<String, Object> stats = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = LocalDateTime.of(today, LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(today, LocalTime.MAX);

        Long totalDevices = deviceMapper.selectCount(null);
        Long onlineDevices = deviceMapper.selectCount(
                new QueryWrapper<Device>().eq("online_status", 1));
        Long offlineDevices = totalDevices - onlineDevices;

        double onlineRate = totalDevices > 0
                ? Math.round(onlineDevices * 1000.0 / totalDevices) / 10.0
                : 0.0;

        Long todayAlarms = alarmRecordMapper.selectCount(
                new QueryWrapper<AlarmRecord>()
                        .ge("alarm_time", todayStart)
                        .le("alarm_time", todayEnd));
        Long unhandledAlarms = alarmRecordMapper.selectCount(
                new QueryWrapper<AlarmRecord>().eq("handle_status", 0));
        Long handledAlarms = alarmRecordMapper.selectCount(
                new QueryWrapper<AlarmRecord>().eq("handle_status", 1));
        Long misreportAlarms = alarmRecordMapper.selectCount(
                new QueryWrapper<AlarmRecord>().eq("handle_status", 2));
        Long highLevelAlarms = alarmRecordMapper.selectCount(
                new QueryWrapper<AlarmRecord>().eq("alarm_level", "HIGH"));

        Long todayHumanCount = humanRecordMapper.selectCount(
                new QueryWrapper<HumanRecord>()
                        .ge("report_time", todayStart)
                        .le("report_time", todayEnd));

        HumanRecord latestHuman = humanRecordMapper.selectOne(
                new QueryWrapper<HumanRecord>()
                        .orderByDesc("report_time")
                        .last("LIMIT 1"));
        int latestHumanDetected = (latestHuman != null && latestHuman.getHumanDetected() != null)
                ? latestHuman.getHumanDetected() : 0;

        stats.put("deviceTotal", totalDevices);
        stats.put("deviceOnline", onlineDevices);
        stats.put("deviceOffline", offlineDevices);
        stats.put("deviceOnlineRate", onlineRate);
        stats.put("todayAlarmCount", todayAlarms);
        stats.put("unhandledAlarmCount", unhandledAlarms);
        stats.put("handledAlarmCount", handledAlarms);
        stats.put("falseAlarmCount", misreportAlarms);
        stats.put("highAlarmCount", highLevelAlarms);
        stats.put("todayHumanCount", todayHumanCount);
        stats.put("latestHumanDetected", latestHumanDetected);
        stats.put("lastUpdateTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return stats;
    }
}
