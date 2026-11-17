package com.example.security.service;

import com.example.security.dto.HumanDataDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 华为云 IoTDA 同步转发服务
 *
 * 将自建 MQTT 收到的人体感应数据同步上报到华为云 IoTDA。
 * 默认关闭（enabled=false），通过环境变量开启。
 * 转发失败不影响原有自建 MQTT 入库和告警流程。
 */
@Service
public class HuaweiIotdaForwardService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MqttClient mqttClient;

    @Value("${huawei.iotda.enabled:false}")
    private boolean enabled;

    @Value("${huawei.iotda.host}")
    private String host;

    @Value("${huawei.iotda.client-id}")
    private String clientId;

    @Value("${huawei.iotda.username}")
    private String username;

    @Value("${huawei.iotda.password}")
    private String password;

    @Value("${huawei.iotda.device-id}")
    private String deviceId;

    @PostConstruct
    public void init() {
        if (!enabled) {
            System.out.println("华为云 IoTDA 转发: 未启用 (huawei.iotda.enabled=false)");
            return;
        }
        connect();
    }

    private void connect() {
        try {
            mqttClient = new MqttClient(host, clientId + "_forward");
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("华为云 IoTDA 连接断开: " + cause.getMessage());
                }
                @Override
                public void messageArrived(String topic, MqttMessage message) {}
                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            mqttClient.connect(options);
            System.out.println("华为云 IoTDA 转发: 连接成功 → " + host);
        } catch (Exception e) {
            System.out.println("华为云 IoTDA 转发: 连接失败 → " + e.getMessage());
            mqttClient = null;
        }
    }

    /**
     * 同步转发数据到华为云 IoTDA
     *
     * @param data 人体感应数据
     */
    public void forward(HumanDataDTO data) {
        if (!enabled || mqttClient == null || !mqttClient.isConnected()) {
            return;
        }

        try {
            String topic = "$oc/devices/" + deviceId + "/sys/messages/up";

            // 构建华为云上报 Payload
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            java.util.Map<String, Object> content = new java.util.LinkedHashMap<>();
            content.put("deviceCode", data.getDeviceCode());
            content.put("humanDetected", data.getHumanDetected());
            content.put("defenseStatus", data.getDefenseStatus());
            content.put("timestamp", data.getTimestamp() != null ? data.getTimestamp()
                    : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            payload.put("content", content);

            String json = objectMapper.writeValueAsString(payload);
            MqttMessage message = new MqttMessage(json.getBytes());
            message.setQos(0);

            mqttClient.publish(topic, message);
            System.out.println("华为云 IoTDA 同步成功 → " + topic);
        } catch (Exception e) {
            System.out.println("华为云 IoTDA 同步失败: " + e.getMessage());
            // 不影响主流程
        }
    }

    @PreDestroy
    public void destroy() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                System.out.println("华为云 IoTDA 转发: 已断开");
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
