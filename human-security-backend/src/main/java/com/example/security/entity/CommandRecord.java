package com.example.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("command_record")
public class CommandRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceCode;
    private String command;
    private String direction;
    private String status;
    private String replyContent;
    private LocalDateTime sendTime;
    private LocalDateTime createTime;
}
