package com.example.security.dto;

import java.util.List;

public class SosRequestDto {
    private String eventType;
    private String urgency;
    private String notes;
    private String location;
    private Long linkedPirAlarmId;
    private String linkedDeviceCode;
    private Boolean notifiedGuard;
    private List<Long> receiverIds;

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Long getLinkedPirAlarmId() { return linkedPirAlarmId; }
    public void setLinkedPirAlarmId(Long linkedPirAlarmId) { this.linkedPirAlarmId = linkedPirAlarmId; }
    public String getLinkedDeviceCode() { return linkedDeviceCode; }
    public void setLinkedDeviceCode(String linkedDeviceCode) { this.linkedDeviceCode = linkedDeviceCode; }
    public Boolean getNotifiedGuard() { return notifiedGuard; }
    public void setNotifiedGuard(Boolean notifiedGuard) { this.notifiedGuard = notifiedGuard; }
    public List<Long> getReceiverIds() { return receiverIds; }
    public void setReceiverIds(List<Long> receiverIds) { this.receiverIds = receiverIds; }
}
