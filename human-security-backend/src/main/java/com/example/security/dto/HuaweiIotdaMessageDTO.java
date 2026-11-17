package com.example.security.dto;

import lombok.Data;

/**
 * 华为云 IoTDA 规则引擎 HTTP 转发消息格式
 */
@Data
public class HuaweiIotdaMessageDTO {
    private HumanDataDTO content;
}
