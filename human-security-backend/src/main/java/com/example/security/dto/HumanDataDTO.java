package com.example.security.dto;

import lombok.Data;

@Data
public class HumanDataDTO {
    private String deviceCode;
    private Integer humanDetected;
    private Integer defenseStatus;
    private String timestamp;
}
