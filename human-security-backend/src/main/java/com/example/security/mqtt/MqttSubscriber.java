package com.example.security.mqtt;

import com.example.security.config.MqttConfig;
import com.example.security.service.MqttMessageService;
import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.stereotype.Component;

@Component
public class MqttSubscriber {

    private final MqttConfig mqttConfig;
    private final MqttMessageService mqttMessageService;
    private MqttClient client;

    public MqttSubscriber(MqttConfig mqttConfig, MqttMessageService mqttMessageService) {
        this.mqttConfig = mqttConfig;
        this.mqttMessageService = mqttMessageService;
    }

    @PostConstruct
    public void connect() {
        // 初始连接重试循环
        int retries = 0;
        while (retries < 30) {
            try {
                client = new MqttClient(
                        mqttConfig.getHost(),
                        mqttConfig.getClientId() + "-subscriber"
                );

                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(false);           // broker 记住订阅，断线重连后自动恢复
                options.setConnectionTimeout(10);
                options.setKeepAliveInterval(60);
                options.setAutomaticReconnect(true);       // 启用 Paho 内置自动重连
                if (mqttConfig.getUsername() != null && !mqttConfig.getUsername().isEmpty()) {
                    options.setUserName(mqttConfig.getUsername());
                }
                if (mqttConfig.getPassword() != null && !mqttConfig.getPassword().isEmpty()) {
                    options.setPassword(mqttConfig.getPassword().toCharArray());
                }

                client.setCallback(new MqttCallback() {
                    @Override
                    public void connectionLost(Throwable cause) {
                        System.out.println("MQTT 连接断开：" + cause.getMessage());
                        // Paho 自动重连后，重新订阅确保 topic 有效
                        try { Thread.sleep(3000); } catch (Exception ignored) {}
                        try { if (client.isConnected()) resubscribe(); } catch (Exception ignored) {}
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        String payload = new String(message.getPayload());
                        mqttMessageService.handleMessage(topic, payload);
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                    }
                });

                client.connect(options);
                resubscribe();
                System.out.println("MQTT 订阅者已连接 → " + mqttConfig.getHost());
                return;  // 连接成功，退出重试循环
            } catch (Exception e) {
                retries++;
                System.out.println("MQTT 连接失败 (" + retries + "/30): " + e.getMessage());
                try { Thread.sleep(10000); } catch (Exception ignored) {}
            }
        }
        System.out.println("MQTT 订阅者：30 次重试后仍未连接，请检查 Broker");
    }

    private void resubscribe() {
        try {
            int qos = mqttConfig.getQos();
            // 主 Topic: topic a (设备上报)
            client.subscribe(mqttConfig.getSubscribeTopic(), qos);
            System.out.println("已订阅 topic a (QoS=" + qos + ")");

            // 兼容旧 Topic
            client.subscribe("human/device/+/data", qos);
            client.subscribe("human/device/+/status", qos);
            client.subscribe("human/device/+/reply", qos);
            client.subscribe("pir/device/+/data", qos);
            client.subscribe("pir/device/+/status", qos);
            client.subscribe("pir/device/+/alarm", qos);
            System.out.println("旧 Topic 兼容订阅完成");
        } catch (Exception e) {
            System.out.println("Topic 订阅失败：" + e.getMessage());
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }
}
