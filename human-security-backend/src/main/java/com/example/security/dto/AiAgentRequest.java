package com.example.security.dto;

import java.util.Map;

public class AiAgentRequest {
    private Long userId;
    private String message;
    private Map<String, Object> systemContext;  // 前端传来的实时设备/告警/PIR/人脸数据
    private String scene;                        // 使用场景，如 SECURITY_STATUS_FOR_VISUALLY_IMPAIRED
    private String instruction;                  // 自定义指令

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Map<String, Object> getSystemContext() { return systemContext; }
    public void setSystemContext(Map<String, Object> systemContext) { this.systemContext = systemContext; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
}
