package com.example.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_message")
public class UserMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String receiverName;
    private String title;
    private String content;
    private Integer readStatus;
    private String messageType;
    private LocalDateTime createTime;
    private LocalDateTime readTime;
    private Integer deletedBySender;
    private Integer deletedByReceiver;
    private Integer isEmergency;

    public Long getId(){return id;}
    public void setId(Long v){this.id=v;}
    public Long getSenderId(){return senderId;}
    public void setSenderId(Long v){this.senderId=v;}
    public String getSenderName(){return senderName;}
    public void setSenderName(String v){this.senderName=v;}
    public Long getReceiverId(){return receiverId;}
    public void setReceiverId(Long v){this.receiverId=v;}
    public String getReceiverName(){return receiverName;}
    public void setReceiverName(String v){this.receiverName=v;}
    public String getTitle(){return title;}
    public void setTitle(String v){this.title=v;}
    public String getContent(){return content;}
    public void setContent(String v){this.content=v;}
    public Integer getReadStatus(){return readStatus;}
    public void setReadStatus(Integer v){this.readStatus=v;}
    public String getMessageType(){return messageType;}
    public void setMessageType(String v){this.messageType=v;}
    public LocalDateTime getCreateTime(){return createTime;}
    public void setCreateTime(LocalDateTime v){this.createTime=v;}
    public LocalDateTime getReadTime(){return readTime;}
    public void setReadTime(LocalDateTime v){this.readTime=v;}
    public Integer getDeletedBySender(){return deletedBySender;}
    public void setDeletedBySender(Integer v){this.deletedBySender=v;}
    public Integer getDeletedByReceiver(){return deletedByReceiver;}
    public void setDeletedByReceiver(Integer v){this.deletedByReceiver=v;}
    public Integer getIsEmergency(){return isEmergency;}
    public void setIsEmergency(Integer v){this.isEmergency=v;}
}
