package com.example.security.dto;

import java.util.List;

public class AiAgentResponse {
    private String answer;
    private String intent;           // SECURITY_STATUS / DEVICE_QUERY / ALARM_QUERY / HELP_ADVICE / KNOWLEDGE_QA / UNKNOWN
    private String riskLevel;        // LOW / MEDIUM / HIGH / CRITICAL
    private List<String> usedTools;  // device_status, alarm_record, kb_knowledge
    private List<String> evidence;
    private List<String> suggestions;
    private ActionDraft actionDraft;
    private String source;           // LLM / RULE / HYBRID / FALLBACK
    private boolean fallback;        // true = 本地兜底回答
    private String model;            // 使用的模型名，如 deepseek-v4-flash
    private String errorType;        // 兜底原因：LLM_REQUEST_FAILED / NO_API_KEY / TIMEOUT / NETWORK_ERROR

    public AiAgentResponse() {
        this.fallback = false;
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public List<String> getUsedTools() { return usedTools; }
    public void setUsedTools(List<String> usedTools) { this.usedTools = usedTools; }
    public List<String> getEvidence() { return evidence; }
    public void setEvidence(List<String> evidence) { this.evidence = evidence; }
    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
    public ActionDraft getActionDraft() { return actionDraft; }
    public void setActionDraft(ActionDraft actionDraft) { this.actionDraft = actionDraft; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public boolean isFallback() { return fallback; }
    public void setFallback(boolean fallback) { this.fallback = fallback; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
}
