package com.example.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttConfig {
    private String host;
    private String clientId;
    private String username;
    private String password;
    private String subscribeTopic = "a";
    private String commandTopic = "b";
    private int qos = 2;
    private int offlineCheckInterval = 10;
    private int offlineThreshold = 30;
}
