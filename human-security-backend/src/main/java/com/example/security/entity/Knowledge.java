package com.example.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("kb_knowledge")
public class Knowledge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String question;
    private String answer;
    private String keywords;
    private String category;
    private Integer enabled;
    private LocalDateTime createdAt;

    // ===== getters =====
    public Long getId() { return id; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public String getKeywords() { return keywords; }
    public String getCategory() { return category; }
    public Integer getEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ===== setters =====
    public void setId(Long id) { this.id = id; }
    public void setQuestion(String question) { this.question = question; }
    public void setAnswer(String answer) { this.answer = answer; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public void setCategory(String category) { this.category = category; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
