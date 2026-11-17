package com.example.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("alarm_record")
public class AlarmRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("device_code")
    private String deviceCode;

    @TableField("device_name")
    private String deviceName;

    @TableField("location")
    private String location;

    @TableField("alarm_type")
    private String alarmType;

    @TableField("alarm_level")
    private String alarmLevel;

    @TableField("alarm_content")
    private String alarmContent;

    @TableField("image_path")
    private String imagePath;

    /** handle_status: 0=UNHANDLED, 1=HANDLED, 2=MISREPORT */
    @TableField("handle_status")
    private Integer handleStatus;

    @TableField("handle_user")
    private String handleUser;

    /** 虚拟字段：不存在于数据库，仅用于内存传递 */
    @TableField(exist = false)
    private String handleResult;

    @TableField("handle_time")
    private LocalDateTime handleTime;

    @TableField("alarm_time")
    private LocalDateTime alarmTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    // ===== 手写 getter / setter =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getAlarmType() { return alarmType; }
    public void setAlarmType(String alarmType) { this.alarmType = alarmType; }

    public String getAlarmLevel() { return alarmLevel; }
    public void setAlarmLevel(String alarmLevel) { this.alarmLevel = alarmLevel; }

    public String getAlarmContent() { return alarmContent; }
    public void setAlarmContent(String alarmContent) { this.alarmContent = alarmContent; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public Integer getHandleStatus() { return handleStatus; }
    public void setHandleStatus(Integer handleStatus) { this.handleStatus = handleStatus; }

    public String getHandleUser() { return handleUser; }
    public void setHandleUser(String handleUser) { this.handleUser = handleUser; }

    public String getHandleResult() { return handleResult; }
    public void setHandleResult(String handleResult) { this.handleResult = handleResult; }

    public LocalDateTime getHandleTime() { return handleTime; }
    public void setHandleTime(LocalDateTime handleTime) { this.handleTime = handleTime; }

    public LocalDateTime getAlarmTime() { return alarmTime; }
    public void setAlarmTime(LocalDateTime alarmTime) { this.alarmTime = alarmTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    // ===== 兼容旧代码的 alarmStatus getter/setter（基于 handleStatus 转换） =====

    public String getAlarmStatus() {
        if (handleStatus == null) return "UNHANDLED";
        switch (handleStatus) {
            case 1:  return "HANDLED";
            case 2:  return "MISREPORT";
            default: return "UNHANDLED";
        }
    }

    public void setAlarmStatus(String alarmStatus) {
        if (alarmStatus == null) {
            this.handleStatus = 0;
            return;
        }
        switch (alarmStatus.toUpperCase()) {
            case "HANDLED":   this.handleStatus = 1; break;
            case "MISREPORT": this.handleStatus = 2; break;
            default:          this.handleStatus = 0; break;  // UNHANDLED
        }
    }

    // ===== 中文名转换方法 =====

    public String getAlarmTypeName() {
        if (alarmType == null) return "未知";
        switch (alarmType.toUpperCase()) {
            case "INTRUSION":          return "入侵告警";
            case "PIR_HUMAN_DETECTED": return "PIR 人体感应告警";
            case "STRANGER":           return "陌生人告警";
            case "FACE_UNKNOWN":       return "陌生人脸告警";
            case "FACE_SPOOF":         return "假体攻击告警";
            case "DEVICE_OFFLINE":     return "设备离线";
            default:                   return alarmType;
        }
    }

    public String getAlarmLevelName() {
        if (alarmLevel == null) return "未知";
        switch (alarmLevel.toUpperCase()) {
            case "HIGH":   return "高";
            case "MIDDLE":
            case "MEDIUM": return "中";
            case "LOW":    return "低";
            default:       return alarmLevel;
        }
    }

    public String getAlarmStatusName() {
        if (handleStatus == null) return "未处理";
        switch (handleStatus) {
            case 0:  return "未处理";
            case 1:  return "已处理";
            case 2:  return "误报";
            default: return "未知";
        }
    }
}
