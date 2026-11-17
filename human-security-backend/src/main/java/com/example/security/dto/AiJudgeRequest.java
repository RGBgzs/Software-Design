package com.example.security.dto;

public class AiJudgeRequest {
    private Long userId;
    private Long alarmId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAlarmId() { return alarmId; }
    public void setAlarmId(Long alarmId) { this.alarmId = alarmId; }
}
