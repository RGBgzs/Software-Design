package com.example.security.dto;

public class VoiceCommandRequest {
    private Long userId;
    private String text;
    private String source;  // VOICE / TEXT

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
