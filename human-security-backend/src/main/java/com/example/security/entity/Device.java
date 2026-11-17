package com.example.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device")
public class Device {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceCode;
    private String deviceName;
    private String location;
    private String deviceType;
    private Long userId;
    private Integer onlineStatus;
    private Integer humanDetected;
    private Integer defenseStatus;
    private LocalDateTime lastReportTime;
    private LocalDateTime lastOnline;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String remark;
}
