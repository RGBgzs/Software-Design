package com.example.security.controller;

import com.example.security.dto.HumanDataDTO;
import com.example.security.service.MqttMessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 华为云 IoTDA 数据接入接口
 * 接收华为云规则引擎 HTTP 转发的设备数据
 *
 * 兼容两种华为云 IoTDA 消息格式：
 *   A. 根节点 content:  { "content": { ... } }
 *   B. 嵌套格式:        { "notify_data": { "body": { "content": { ... } } } }
 */
@RestController
@RequestMapping("/api/huawei")
public class HuaweiIotdaController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MqttMessageService mqttMessageService;

    /**
     * 接收华为云 IoTDA 规则引擎转发的消息
     */
    @PostMapping("/iotda/message")
    public Map<String, Object> receiveMessage(@RequestBody String rawBody) {
        Map<String, Object> result = new HashMap<>();

        // 打印华为云转发过来的完整原始 JSON
        System.out.println("========================================");
        System.out.println("收到华为云IoTDA原始数据：");
        System.out.println(rawBody);
        System.out.println("========================================");

        try {
            JsonNode root = objectMapper.readTree(rawBody);

            // 提取 content：兼容两种格式
            JsonNode contentNode = extractContent(root);

            if (contentNode == null) {
                result.put("code", 400);
                result.put("msg", "消息格式错误，缺少 content 字段");
                result.put("data", null);
                return result;
            }

            // 将 content 转为 HumanDataDTO
            HumanDataDTO data = objectMapper.treeToValue(contentNode, HumanDataDTO.class);

            if (data.getDeviceCode() == null || data.getDeviceCode().trim().isEmpty()) {
                result.put("code", 400);
                result.put("msg", "设备编号不能为空");
                result.put("data", null);
                return result;
            }

            // 日志：提取后的业务数据
            System.out.println("华为云IoTDA解析结果 → deviceCode=" + data.getDeviceCode()
                    + " humanDetected=" + data.getHumanDetected()
                    + " defenseStatus=" + data.getDefenseStatus()
                    + " timestamp=" + data.getTimestamp());

            // 复用现有 MQTT 数据处理逻辑
            mqttMessageService.processHumanData(data);

            result.put("code", 200);
            result.put("msg", "华为云IoTDA消息接收成功");
            result.put("data", null);
            return result;

        } catch (Exception e) {
            System.out.println("华为云IoTDA消息解析失败：" + e.getMessage());
            result.put("code", 400);
            result.put("msg", "消息解析失败：" + e.getMessage());
            result.put("data", null);
            return result;
        }
    }

    /**
     * 从 JSON 中提取 content 节点，兼容两种格式：
     *   A. { "content": { ... } }                              → 根节点直接取 content
     *   B. { "notify_data": { "body": { "content": { ... } } } } → 嵌套路径取 notify_data/body/content
     */
    private JsonNode extractContent(JsonNode root) {
        // 格式 A：根节点 content
        if (root.has("content") && !root.get("content").isNull()) {
            System.out.println("华为云IoTDA消息格式匹配 → 根节点 content");
            return root.get("content");
        }

        // 格式 B：notify_data.body.content（华为云 IoTDA 标准转发格式）
        if (root.has("notify_data")) {
            JsonNode notifyData = root.get("notify_data");
            if (notifyData.has("body")) {
                JsonNode body = notifyData.get("body");
                if (body.has("content") && !body.get("content").isNull()) {
                    JsonNode content = body.get("content");
                    // content 可能是对象也可能是 JSON 字符串
                    if (content.isTextual()) {
                        try {
                            System.out.println("华为云IoTDA消息格式匹配 → notify_data.body.content（JSON字符串）");
                            return objectMapper.readTree(content.asText());
                        } catch (Exception e) {
                            System.out.println("华为云IoTDA消息 content 字符串解析失败：" + e.getMessage());
                            return null;
                        }
                    }
                    System.out.println("华为云IoTDA消息格式匹配 → notify_data.body.content（JSON对象）");
                    return content;
                }
            }
        }

        return null;
    }
}
