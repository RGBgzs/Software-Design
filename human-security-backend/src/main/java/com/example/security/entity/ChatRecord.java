package com.example.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("chat_record")
public class ChatRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("user_message")
    private String userMessage;

    @TableField("bot_answer")
    private String botAnswer;

    @TableField("matched_knowledge_id")
    private Long matchedKnowledgeId;

    @TableField("category")
    private String category;

    @TableField("created_at")
    private LocalDateTime createdAt;

    // ===== getter / setter =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }

    public String getBotAnswer() { return botAnswer; }
    public void setBotAnswer(String botAnswer) { this.botAnswer = botAnswer; }

    public Long getMatchedKnowledgeId() { return matchedKnowledgeId; }
    public void setMatchedKnowledgeId(Long matchedKnowledgeId) { this.matchedKnowledgeId = matchedKnowledgeId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
