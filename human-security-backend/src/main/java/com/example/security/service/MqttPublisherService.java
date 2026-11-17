package com.example.security.service;

import com.example.security.config.MqttConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.stereotype.Service;

@Service
public class MqttPublisherService {

    private final MqttConfig mqttConfig;
    private MqttClient client;

    public MqttPublisherService(MqttConfig mqttConfig) {
        this.mqttConfig = mqttConfig;
    }

    @PostConstruct
    public void init() {
        try {
            client = new MqttClient(mqttConfig.getHost(), mqttConfig.getClientId() + "-publisher");
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(60);
            // MQTT 3.1.1 — 无用户名、无密码
            if (mqttConfig.getUsername() != null && !mqttConfig.getUsername().isEmpty()) {
                options.setUserName(mqttConfig.getUsername());
            }
            if (mqttConfig.getPassword() != null && !mqttConfig.getPassword().isEmpty()) {
                options.setPassword(mqttConfig.getPassword().toCharArray());
            }
            client.connect(options);
            System.out.println("MQTT 发布者已连接");
        } catch (Exception e) {
            System.out.println("MQTT 发布者连接失败：" + e.getMessage());
        }
    }

    /**
     * 向 topic b 发布命令（QoS 2）
     */
    public boolean publish(String topic, String payload) {
        try {
            if (client == null || !client.isConnected()) {
                System.out.println("MQTT 发布者未连接，尝试重连...");
                init();
                if (client == null || !client.isConnected()) {
                    return false;
                }
            }
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(mqttConfig.getQos());
            client.publish(topic, message);
            System.out.println("MQTT 发布成功 → Topic: " + topic + ", Payload: " + payload + " (QoS=" + mqttConfig.getQos() + ")");
            return true;
        } catch (Exception e) {
            System.out.println("MQTT 发布失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 向 topic b 发布命令
     */
    public boolean publishCommand(String payload) {
        return publish(mqttConfig.getCommandTopic(), payload);
    }

    /**
     * 向指定 topic 发布命令
     */
    public boolean publishToTopic(String topic, String payload) {
        return publish(topic, payload);
    }

    @PreDestroy
    public void destroy() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                client.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
