package com.example.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("human_record")
public class HumanRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceCode;
    private String deviceName;
    private String location;
    private Integer humanDetected;
    private Integer defenseStatus;
    private Integer isAlarm;
    private Long alarmId;
    private String rawData;
    private LocalDateTime reportTime;
    private LocalDateTime createTime;
}
