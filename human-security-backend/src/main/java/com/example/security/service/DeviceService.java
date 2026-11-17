package com.example.security.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.security.entity.*;
import com.example.security.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class DeviceService {

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private HumanRecordMapper humanRecordMapper;

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    @Autowired
    private CommandRecordMapper commandRecordMapper;

    @Autowired
    private MqttPublisherService mqttPublisherService;

    @Autowired
    private UserMapper userMapper;

    /** 查询设备状态 */
    public Map<String, Object> getDeviceStatus(String deviceCode) {
        Device device = deviceMapper.selectOne(
                new QueryWrapper<Device>().eq("device_code", deviceCode));
        if (device == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("deviceCode", device.getDeviceCode());
        result.put("deviceName", device.getDeviceName());
        result.put("onlineStatus", device.getOnlineStatus());
        result.put("humanDetected", device.getHumanDetected());
        result.put("defenseStatus", device.getDefenseStatus());
        result.put("lastReportTime", device.getLastReportTime() != null
                ? device.getLastReportTime().toString() : null);
        result.put("lastOnline", device.getLastOnline());
        return result;
    }

    /** 查询人体感应历史 */
    public List<HumanRecord> getRecords(Integer limit) {
        return humanRecordMapper.selectList(
                new QueryWrapper<HumanRecord>()
                        .orderByDesc("id")
                        .last("LIMIT " + (limit != null ? limit : 50)));
    }

    /** 查询告警记录（支持按角色过滤） */
    public List<AlarmRecord> getAlarms(Integer limit) {
        return getAlarms(limit, null, "admin");
    }

    /** 查询告警记录（按角色过滤：admin看全部, guard看管理的用户的设备, user看自己的设备） */
    public List<AlarmRecord> getAlarms(Integer limit, Long userId, String role) {
        if (userId == null || "admin".equals(role)) {
            return alarmRecordMapper.selectList(
                    new QueryWrapper<AlarmRecord>()
                            .orderByDesc("id")
                            .last("LIMIT " + (limit != null ? limit : 50)));
        }

        List<String> deviceCodes = getVisibleDeviceCodes(userId, role);
        if (deviceCodes.isEmpty()) {
            return Collections.emptyList();
        }

        return alarmRecordMapper.selectList(
                new QueryWrapper<AlarmRecord>()
                        .in("device_code", deviceCodes)
                        .orderByDesc("id")
                        .last("LIMIT " + (limit != null ? limit : 50)));
    }

    public List<String> getVisibleDeviceCodes(Long userId, String role) {
        List<Device> devices;
        if ("guard".equals(role)) {
            List<User> managedUsers = userMapper.selectList(
                    new QueryWrapper<User>().eq("guard_id", userId));
            List<Long> managedUserIds = new ArrayList<>();
            for (User u : managedUsers) managedUserIds.add(u.getId());
            if (managedUserIds.isEmpty()) return Collections.emptyList();
            devices = deviceMapper.selectList(
                    new QueryWrapper<Device>().in("user_id", managedUserIds));
        } else {
            devices = deviceMapper.selectList(
                    new QueryWrapper<Device>().eq("user_id", userId));
        }
        List<String> codes = new ArrayList<>();
        for (Device d : devices) codes.add(d.getDeviceCode());
        return codes;
    }

    /** 布防 — 向 topic b 发布 "1" */
    public Map<String, Object> defenseOn(String deviceCode, String operator) {
        Map<String, Object> result = new HashMap<>();

        // 1. 判断设备是否存在
        Device device = deviceMapper.selectOne(
                new QueryWrapper<Device>().eq("device_code", deviceCode));
        if (device == null) {
            result.put("success", false);
            result.put("message", "设备不存在：" + deviceCode);
            return result;
        }

        // 2. 判断当前状态 — 已布防时不再重复下发
        if (device.getDefenseStatus() != null && device.getDefenseStatus() == 1) {
            result.put("success", false);
            result.put("message", "设备已处于布防状态，无需重复操作");
            result.put("deviceCode", deviceCode);
            result.put("defenseStatus", 1);
            return result;
        }

        // 3. MQTT 发布 "1" 到 topic b, QoS 2
        boolean sent = mqttPublisherService.publishCommand("1");

        // 4. 记录指令
        CommandRecord cr = new CommandRecord();
        cr.setDeviceCode(deviceCode);
        cr.setCommand("DEFENSE_ON");
        cr.setDirection("SEND");
        cr.setStatus(sent ? "PENDING" : "FAILED");
        cr.setSendTime(LocalDateTime.now());
        cr.setCreateTime(LocalDateTime.now());
        commandRecordMapper.insert(cr);

        // 5. MQTT 发布成功后更新设备状态
        if (sent) {
            device.setDefenseStatus(1);
            device.setUpdateTime(LocalDateTime.now());
            deviceMapper.updateById(device);
        }

        result.put("success", sent);
        result.put("message", sent ? "布防指令已下发，等待设备确认" : "MQTT 指令发送失败，请检查设备连接");
        result.put("deviceCode", deviceCode);
        result.put("command", "DEFENSE_ON");
        result.put("commandId", cr.getId());
        return result;
    }

    /** 撤防 — 向 topic b 发布 "0" */
    public Map<String, Object> defenseOff(String deviceCode, String operator) {
        Map<String, Object> result = new HashMap<>();

        // 1. 判断设备是否存在
        Device device = deviceMapper.selectOne(
                new QueryWrapper<Device>().eq("device_code", deviceCode));
        if (device == null) {
            result.put("success", false);
            result.put("message", "设备不存在：" + deviceCode);
            return result;
        }

        // 2. 判断当前状态 — 已撤防时不再重复下发
        if (device.getDefenseStatus() == null || device.getDefenseStatus() == 0) {
            result.put("success", false);
            result.put("message", "设备已处于撤防状态，无需重复操作");
            result.put("deviceCode", deviceCode);
            result.put("defenseStatus", 0);
            return result;
        }

        // 3. MQTT 发布 "0" 到 topic b, QoS 2
        boolean sent = mqttPublisherService.publishCommand("0");

        // 4. 记录指令
        CommandRecord cr = new CommandRecord();
        cr.setDeviceCode(deviceCode);
        cr.setCommand("DEFENSE_OFF");
        cr.setDirection("SEND");
        cr.setStatus(sent ? "PENDING" : "FAILED");
        cr.setSendTime(LocalDateTime.now());
        cr.setCreateTime(LocalDateTime.now());
        commandRecordMapper.insert(cr);

        // 5. MQTT 发布成功后更新设备状态
        if (sent) {
            device.setDefenseStatus(0);
            device.setUpdateTime(LocalDateTime.now());
            deviceMapper.updateById(device);
        }

        result.put("success", sent);
        result.put("message", sent ? "撤防指令已下发，等待设备确认" : "MQTT 指令发送失败，请检查设备连接");
        result.put("deviceCode", deviceCode);
        result.put("command", "DEFENSE_OFF");
        result.put("commandId", cr.getId());
        return result;
    }

    // ==================== 设备 CRUD ====================

    /** 获取所有设备 */
    public List<Device> getDevices() {
        return deviceMapper.selectList(new QueryWrapper<Device>().orderByAsc("id"));
    }

    /** 获取设备（按角色过滤：admin看全部, guard看管理的用户的设备, user看自己的） */
    public List<Device> getDevices(Long userId, String role) {
        if (userId == null || "admin".equals(role)) {
            return deviceMapper.selectList(new QueryWrapper<Device>().orderByAsc("id"));
        }
        if ("guard".equals(role)) {
            List<User> managedUsers = userMapper.selectList(
                    new QueryWrapper<User>().eq("guard_id", userId));
            List<Long> managedUserIds = new ArrayList<>();
            for (User u : managedUsers) managedUserIds.add(u.getId());
            if (managedUserIds.isEmpty()) return Collections.emptyList();
            return deviceMapper.selectList(
                    new QueryWrapper<Device>().in("user_id", managedUserIds).orderByAsc("id"));
        }
        return deviceMapper.selectList(
                new QueryWrapper<Device>().eq("user_id", userId).orderByAsc("id"));
    }

    /** 新增设备 */
    public boolean addDevice(Device device) {
        Device exist = deviceMapper.selectOne(
                new QueryWrapper<Device>().eq("device_code", device.getDeviceCode()));
        if (exist != null) return false;
        device.setCreateTime(LocalDateTime.now());
        device.setUpdateTime(LocalDateTime.now());
        return deviceMapper.insert(device) > 0;
    }

    /** 更新设备 */
    public boolean updateDevice(Device device) {
        Device exist = deviceMapper.selectById(device.getId());
        if (exist == null) return false;
        device.setUpdateTime(LocalDateTime.now());
        return deviceMapper.updateById(device) > 0;
    }

    /** 删除设备 */
    public boolean deleteDevice(Long id) {
        return deviceMapper.deleteById(id) > 0;
    }

    // ==================== 告警处理 ====================

    /** 处理告警（用户和保安均可操作）
     *  @param handleStatus 1=确认处理, 2=误报 */
    public boolean handleAlarm(Long id, Integer handleStatus, String handleUser, String remark) {
        AlarmRecord alarm = alarmRecordMapper.selectById(id);
        if (alarm == null) return false;
        alarm.setHandleStatus(handleStatus != null ? handleStatus : 1);
        alarm.setHandleUser(handleUser != null ? handleUser : "admin");
        alarm.setHandleResult(remark != null ? remark
                : (handleStatus != null && handleStatus == 2 ? "确认为误报" : "已确认处理"));
        alarm.setHandleTime(LocalDateTime.now());
        return alarmRecordMapper.updateById(alarm) > 0;
    }

    /** 确认处理告警（兼容旧调用） */
    public boolean handleAlarm(Long id, String handleUser, String handleResult) {
        return handleAlarm(id, 1, handleUser, handleResult);
    }

    // ==================== 设备统计（来自 device 表） ====================

    /** 获取设备统计数据 */
    public Map<String, Object> getDeviceStats() {
        Map<String, Object> data = new HashMap<>();

        Long total = deviceMapper.selectCount(null);
        Long online = deviceMapper.selectCount(
                new QueryWrapper<Device>().eq("online_status", 1));
        Long offline = total - online;
        Long armed = deviceMapper.selectCount(
                new QueryWrapper<Device>().eq("defense_status", 1));
        Long disarmed = total - armed;
        Long humanDetected = deviceMapper.selectCount(
                new QueryWrapper<Device>().eq("human_detected", 1));

        double onlineRate = total > 0
                ? Math.round(online * 1000.0 / total) / 10.0
                : 0.0;

        data.put("total", total);
        data.put("online", online);
        data.put("offline", offline);
        data.put("onlineRate", onlineRate);
        data.put("armed", armed);
        data.put("disarmed", disarmed);
        data.put("humanDetected", humanDetected);
        data.put("lastUpdateTime", LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return data;
    }
}
