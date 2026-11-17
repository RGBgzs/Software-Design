package com.example.security.dto;

import java.util.Map;

public class VoiceCommandResponse {
    private String answer;
    private String intent;
    private String actionType;
    private String target;
    private Boolean needConfirm;
    private String riskLevel;
    private Map<String, Object> actionData;

    public VoiceCommandResponse() {}

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public Boolean getNeedConfirm() { return needConfirm; }
    public void setNeedConfirm(Boolean needConfirm) { this.needConfirm = needConfirm; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public Map<String, Object> getActionData() { return actionData; }
    public void setActionData(Map<String, Object> actionData) { this.actionData = actionData; }
}
