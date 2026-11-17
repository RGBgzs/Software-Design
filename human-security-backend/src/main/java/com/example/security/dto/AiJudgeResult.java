package com.example.security.dto;

import java.util.List;

public class AiJudgeResult {
    private String riskLevel;      // LOW / MEDIUM / HIGH / CRITICAL
    private String eventType;      // NORMAL / DEVICE_OFFLINE / LOW_BATTERY / INTRUSION / MULTI_RISK / UNKNOWN
    private String summary;
    private String reason;
    private List<String> suggestions;
    private boolean needHumanConfirm;
    private double confidence;
    private String source;         // RULE / LLM / FALLBACK
    private Long relatedAlarmId;
    private boolean fallback;      // true = 本地兜底
    private String model;          // 使用的模型名
    private String errorType;      // 兜底原因

    public AiJudgeResult() {
        this.fallback = false;
    }

    public AiJudgeResult(String riskLevel, String eventType, String summary, String reason,
                         List<String> suggestions, double confidence, String source) {
        this.riskLevel = riskLevel;
        this.eventType = eventType;
        this.summary = summary;
        this.reason = reason;
        this.suggestions = suggestions;
        this.needHumanConfirm = !"LOW".equals(riskLevel);
        this.confidence = confidence;
        this.source = source;
    }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
    public boolean isNeedHumanConfirm() { return needHumanConfirm; }
    public void setNeedHumanConfirm(boolean needHumanConfirm) { this.needHumanConfirm = needHumanConfirm; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getRelatedAlarmId() { return relatedAlarmId; }
    public void setRelatedAlarmId(Long relatedAlarmId) { this.relatedAlarmId = relatedAlarmId; }
    public boolean isFallback() { return fallback; }
    public void setFallback(boolean fallback) { this.fallback = fallback; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
}
