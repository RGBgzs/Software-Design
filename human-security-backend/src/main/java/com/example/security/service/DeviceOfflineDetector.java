package com.example.security.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.security.config.MqttConfig;
import com.example.security.entity.AlarmRecord;
import com.example.security.entity.Device;
import com.example.security.mapper.AlarmRecordMapper;
import com.example.security.mapper.DeviceMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时离线检测：每 N 秒检查一次设备 last_report_time
 * 超过阈值则设置 online_status = 0
 */
@Component
public class DeviceOfflineDetector {

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private MqttConfig mqttConfig;

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        int interval = mqttConfig.getOfflineCheckInterval();
        int threshold = mqttConfig.getOfflineThreshold();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "device-offline-detector");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                checkOfflineDevices(threshold);
            } catch (Exception e) {
                System.out.println("离线检测异常: " + e.getMessage());
            }
        }, interval, interval, TimeUnit.SECONDS);

        System.out.println("设备离线检测已启动: 每" + interval + "秒检测, 阈值" + threshold + "秒");
    }

    private void checkOfflineDevices(int thresholdSeconds) {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(thresholdSeconds);
        List<Device> devices = deviceMapper.selectList(
                new QueryWrapper<Device>().eq("online_status", 1));

        int offlineCount = 0;
        for (Device device : devices) {
            if (device.getLastReportTime() == null
                    || device.getLastReportTime().isBefore(threshold)) {
                device.setOnlineStatus(0);
                device.setUpdateTime(LocalDateTime.now());
                deviceMapper.updateById(device);
                offlineCount++;

                // 创建设备离线告警（5分钟内去重）
                boolean alreadyAlarmed = alarmRecordMapper.selectCount(
                        new QueryWrapper<AlarmRecord>()
                                .eq("device_code", device.getDeviceCode())
                                .eq("alarm_type", "DEVICE_OFFLINE")
                                .eq("handle_status", 0)
                                .ge("create_time", LocalDateTime.now().minusMinutes(5))
                ) > 0;
                if (!alreadyAlarmed) {
                    AlarmRecord alarm = new AlarmRecord();
                    alarm.setDeviceCode(device.getDeviceCode());
                    alarm.setDeviceName(device.getDeviceName() != null ? device.getDeviceName() : device.getDeviceCode());
                    alarm.setLocation(device.getLocation());
                    alarm.setAlarmType("DEVICE_OFFLINE");
                    alarm.setAlarmLevel("MEDIUM");
                    alarm.setAlarmContent("设备 " + (device.getDeviceName() != null ? device.getDeviceName() : device.getDeviceCode()) + " 离线" + " [" + LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")) + "]");
                    alarm.setAlarmStatus("UNHANDLED");
                    alarm.setHandleStatus(0);
                    alarm.setAlarmTime(LocalDateTime.now());
                    alarm.setCreateTime(LocalDateTime.now());
                    alarmRecordMapper.insert(alarm);
                }
            }
        }

        if (offlineCount > 0) {
            System.out.println("离线检测: " + offlineCount + " 台设备标记为离线");
        }
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
