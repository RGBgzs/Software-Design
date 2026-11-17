package com.example.security.service;

import com.example.security.entity.AlarmRecord;
import com.example.security.mapper.AlarmRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AlarmRecordService {

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    /**
     * 查询告警列表，支持可选 status / level 参数
     * status: UNHANDLED → 0, HANDLED → 1
     */
    public List<AlarmRecord> list(String status, String level) {
        Integer statusCode = parseStatus(status);

        if (statusCode != null && level != null && !level.isEmpty()) {
            return alarmRecordMapper.selectByStatusAndLevel(statusCode, level);
        } else if (statusCode != null) {
            return alarmRecordMapper.selectByHandleStatus(statusCode);
        } else if (level != null && !level.isEmpty()) {
            return alarmRecordMapper.selectByLevel(level);
        } else {
            return alarmRecordMapper.selectAll();
        }
    }

    private Integer parseStatus(String status) {
        if (status == null || status.isEmpty()) return null;
        switch (status.toUpperCase()) {
            case "UNHANDLED": return 0;
            case "HANDLED":   return 1;
            case "MISREPORT": return 2;
            default:          return null;
        }
    }

    /**
     * 查询最新一条未处理告警
     */
    public AlarmRecord getLatestUnhandled() {
        return alarmRecordMapper.selectLatestUnhandled();
    }

    /**
     * 查询所有未处理告警
     */
    public List<AlarmRecord> getUnhandled() {
        return alarmRecordMapper.selectUnhandled();
    }

    /**
     * 处理告警：标记为已处理
     */
    public boolean handle(Long id) {
        return alarmRecordMapper.handleAlarm(id, LocalDateTime.now()) > 0;
    }

    /**
     * 构建未处理告警列表文本摘要
     */
    public String buildAlarmSummary(List<AlarmRecord> alarms) {
        if (alarms == null || alarms.isEmpty()) {
            return "当前暂无未处理告警，系统处于正常监测状态。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("当前存在未处理告警：\n\n");

        for (int i = 0; i < alarms.size(); i++) {
            AlarmRecord a = alarms.get(i);
            String time = (a.getAlarmTime() != null)
                    ? a.getAlarmTime().toString().replace("T", " ")
                    : "未知";

            sb.append(i + 1).append(". 设备：").append(a.getDeviceName())
              .append("\n   类型：").append(a.getAlarmTypeName())
              .append("\n   等级：").append(a.getAlarmLevelName())
              .append("\n   状态：").append(a.getAlarmStatusName())
              .append("\n   时间：").append(time)
              .append("\n   内容：").append(a.getAlarmContent())
              .append("\n");
        }

        sb.append("\n处理建议：\n请先查看现场情况，如确认为异常入侵，请及时联系紧急联系人或小区保安。");

        return sb.toString();
    }

    /**
     * 构建口语化告警播报文本（供语音播报使用）
     */
    public String buildAlarmSpeechText(AlarmRecord alarm) {
        if (alarm == null) return "";
        return "检测到新的未处理告警：" + alarm.getAlarmContent()
                + "，告警等级：" + alarm.getAlarmLevelName()
                + "，设备：" + alarm.getDeviceName()
                + "，请及时处理。";
    }

    /**
     * 构建全部告警摘要
     */
    public String buildAllAlarmSummary(List<AlarmRecord> alarms) {
        if (alarms == null || alarms.isEmpty()) {
            return "当前系统没有告警记录。";
        }

        long unhandled = alarms.stream()
                .filter(a -> a.getHandleStatus() != null && a.getHandleStatus() == 0)
                .count();

        StringBuilder sb = new StringBuilder();
        sb.append("当前告警情况如下：\n\n");

        for (int i = 0; i < alarms.size(); i++) {
            AlarmRecord a = alarms.get(i);
            String time = (a.getAlarmTime() != null)
                    ? a.getAlarmTime().toString().replace("T", " ")
                    : "未知";

            sb.append(i + 1).append(". ")
              .append(a.getDeviceName())
              .append("：").append(a.getAlarmContent())
              .append("，告警等级：").append(a.getAlarmLevelName())
              .append("，状态：").append(a.getAlarmStatusName())
              .append("，时间：").append(time)
              .append("\n");
        }

        if (unhandled > 0) {
            sb.append("\n系统建议：请先确认现场情况，如确认为异常入侵，请及时联系紧急联系人或小区保安。");
        }

        return sb.toString();
    }
}
