package com.example.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("emergency_help_event")
public class EmergencyHelpEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long initiatorId;
    private String eventType;
    private String urgency;
    private String status;
    private String location;
    private String notes;
    private Long linkedPirAlarmId;
    private String linkedDeviceCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInitiatorId() { return initiatorId; }
    public void setInitiatorId(Long initiatorId) { this.initiatorId = initiatorId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getLinkedPirAlarmId() { return linkedPirAlarmId; }
    public void setLinkedPirAlarmId(Long linkedPirAlarmId) { this.linkedPirAlarmId = linkedPirAlarmId; }
    public String getLinkedDeviceCode() { return linkedDeviceCode; }
    public void setLinkedDeviceCode(String linkedDeviceCode) { this.linkedDeviceCode = linkedDeviceCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
