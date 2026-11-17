package com.example.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("device_status")
public class DeviceStatus {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceCode;
    private String deviceName;
    private String deviceType;
    private String location;
    private Integer online;
    private Integer defense;
    private Integer battery;
    private LocalDateTime lastReportTime;
    private LocalDateTime updatedAt;

    // ===== getters =====
    public Long getId() { return id; }
    public String getDeviceCode() { return deviceCode; }
    public String getDeviceName() { return deviceName; }
    public String getDeviceType() { return deviceType; }
    public String getLocation() { return location; }
    public Integer getOnline() { return online; }
    public Integer getDefense() { return defense; }
    public Integer getBattery() { return battery; }
    public LocalDateTime getLastReportTime() { return lastReportTime; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // ===== setters =====
    public void setId(Long id) { this.id = id; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public void setLocation(String location) { this.location = location; }
    public void setOnline(Integer online) { this.online = online; }
    public void setDefense(Integer defense) { this.defense = defense; }
    public void setBattery(Integer battery) { this.battery = battery; }
    public void setLastReportTime(LocalDateTime lastReportTime) { this.lastReportTime = lastReportTime; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
