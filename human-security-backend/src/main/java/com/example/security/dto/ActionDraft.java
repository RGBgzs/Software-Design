package com.example.security.dto;

public class ActionDraft {
    private String type;    // HELP_MESSAGE / ALARM_MESSAGE / NONE
    private String title;   // 消息标题
    private String content; // 消息内容

    public ActionDraft() {}
    public ActionDraft(String type, String title, String content) {
        this.type = type;
        this.title = title;
        this.content = content;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
