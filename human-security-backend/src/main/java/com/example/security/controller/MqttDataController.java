package com.example.security.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.security.dto.HumanDataDTO;
import com.example.security.entity.Device;
import com.example.security.mapper.DeviceMapper;
import com.example.security.service.MqttMessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * MQTT HTTP 数据接入接口
 * 接收华为云 IoTDA 规则引擎 HTTP 推送的设备数据
 *
 * 兼容三种消息格式：
 *   A. 根节点 content:  { "content": { "deviceCode":"...", ... } }
 *   B. 标准 content:    { "notify_data": { "body": { "content": { ... } } } }
 *   C. 属性上报 services: { "notify_data": { "header":{...}, "body":{ "services":[...] } } }
 */
@RestController
@RequestMapping("/api")
public class MqttDataController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MqttMessageService mqttMessageService;

    @Autowired
    private DeviceMapper deviceMapper;

    /**
     * 接收华为云 IoTDA 规则引擎 HTTP 推送
     * 华为云配置的目标地址: POST http://47.108.58.107:8089/api/mqtt/data
     */
    @PostMapping("/mqtt/data")
    public Map<String, Object> receiveMqttData(@RequestBody String rawBody) {
        Map<String, Object> result = new HashMap<>();

        System.out.println("========================================");
        System.out.println("收到 MQTT HTTP 推送数据：");
        System.out.println(rawBody);
        System.out.println("========================================");

        try {
            JsonNode root = objectMapper.readTree(rawBody);

            // 依次尝试三种格式解析
            HumanDataDTO data = null;

            // 格式 C：属性上报 services.Infrared_Status（小熊派 BearPi）
            data = tryParseServicesFormat(root);
            if (data == null) {
                // 格式 A / B：content 格式
                JsonNode contentNode = extractContent(root);
                if (contentNode != null) {
                    data = objectMapper.treeToValue(contentNode, HumanDataDTO.class);
                    if (data.getDeviceCode() == null || data.getDeviceCode().trim().isEmpty()) {
                        result.put("code", 400);
                        result.put("msg", "设备编号不能为空");
                        result.put("data", null);
                        return result;
                    }
                }
            }

            if (data == null) {
                result.put("code", 400);
                result.put("msg", "消息格式错误，无法解析（支持 content 和 services 格式）");
                result.put("data", null);
                return result;
            }

            System.out.println("MQTT HTTP 解析结果 → deviceCode=" + data.getDeviceCode()
                    + " humanDetected=" + data.getHumanDetected()
                    + " defenseStatus=" + data.getDefenseStatus()
                    + " timestamp=" + data.getTimestamp());

            mqttMessageService.processHumanData(data);

            result.put("code", 200);
            result.put("msg", "MQTT数据接收成功");
            result.put("data", null);
            return result;

        } catch (Exception e) {
            System.out.println("MQTT HTTP 数据解析失败：" + e.getMessage());
            result.put("code", 400);
            result.put("msg", "消息解析失败：" + e.getMessage());
            result.put("data", null);
            return result;
        }
    }

    // ==================== 格式 A / B：content 提取 ====================

    private JsonNode extractContent(JsonNode root) {
        // 格式 A：根节点 content
        if (root.has("content") && !root.get("content").isNull()) {
            System.out.println("MQTT HTTP 格式匹配 → 根节点 content");
            return root.get("content");
        }

        // 格式 B：notify_data.body.content
        if (root.has("notify_data")) {
            JsonNode notifyData = root.get("notify_data");
            if (notifyData.has("body")) {
                JsonNode body = notifyData.get("body");
                if (body.has("content") && !body.get("content").isNull()) {
                    JsonNode content = body.get("content");
                    if (content.isTextual()) {
                        try {
                            System.out.println("MQTT HTTP 格式匹配 → notify_data.body.content（字符串）");
                            return objectMapper.readTree(content.asText());
                        } catch (Exception e) {
                            System.out.println("content 字符串解析失败：" + e.getMessage());
                            return null;
                        }
                    }
                    System.out.println("MQTT HTTP 格式匹配 → notify_data.body.content（对象）");
                    return content;
                }
            }
        }

        return null;
    }

    // ==================== 格式 C：属性上报 services ====================

    /**
     * 解析华为云属性上报格式（小熊派 BearPi-HM_Nano D11 infrared）
     *
     * {
     *   "notify_data": {
     *     "header": { "device_id": "6a43868f7f2e6c302f80868e_2020111409" },
     *     "body": {
     *       "services": [
     *         { "service_id": "Infrared", "properties": { "Infrared_Status": "Intrude" } }
     *       ]
     *     }
     *   }
     * }
     */
    private HumanDataDTO tryParseServicesFormat(JsonNode root) {
        if (!root.has("notify_data")) return null;

        JsonNode notifyData = root.get("notify_data");
        if (!notifyData.has("body")) return null;

        JsonNode body = notifyData.get("body");
        if (!body.has("services") || !body.get("services").isArray()) return null;

        System.out.println("MQTT HTTP 格式匹配 → 华为云属性上报 services");

        // 1. 从 header 读取 device_id
        String huaweiDeviceId = null;
        if (notifyData.has("header")) {
            JsonNode header = notifyData.get("header");
            if (header.has("device_id")) {
                huaweiDeviceId = header.get("device_id").asText();
                System.out.println("华为云 device_id = " + huaweiDeviceId);
            }
        }

        // 2. 映射 device_id → deviceCode
        String deviceCode = mapDeviceId(huaweiDeviceId);

        // 3. 从 services 中找 Infrared 服务
        Integer humanDetected = null;
        for (JsonNode svc : body.get("services")) {
            if (!svc.has("service_id")) continue;
            if (!"Infrared".equals(svc.get("service_id").asText())) continue;

            if (!svc.has("properties")) continue;
            JsonNode props = svc.get("properties");

            if (props.has("Infrared_Status")) {
                String status = props.get("Infrared_Status").asText();
                System.out.println("Infrared_Status 原始值 = " + status);

                if ("Intrude".equalsIgnoreCase(status)) {
                    humanDetected = 1;
                    System.out.println("转换: Infrared_Status=" + status + " → humanDetected=1");
                } else if ("Safe".equalsIgnoreCase(status)) {
                    humanDetected = 0;
                    System.out.println("转换: Infrared_Status=" + status + " → humanDetected=0");
                } else {
                    System.out.println("未知 Infrared_Status 值: " + status + "，跳过");
                }
            }
            break;
        }

        if (humanDetected == null) {
            System.out.println("未找到 Infrared.Infrared_Status 字段");
            return null;
        }

        // 4. 从数据库读取当前 defenseStatus
        Integer defenseStatus = 0;
        if (deviceCode != null) {
            Device device = deviceMapper.selectOne(
                    new QueryWrapper<Device>().eq("device_code", deviceCode));
            if (device != null && device.getDefenseStatus() != null) {
                defenseStatus = device.getDefenseStatus();
            }
        }
        System.out.println("从数据库读取 defenseStatus = " + defenseStatus);

        // 5. 构建 HumanDataDTO
        HumanDataDTO data = new HumanDataDTO();
        data.setDeviceCode(deviceCode);
        data.setHumanDetected(humanDetected);
        data.setDefenseStatus(defenseStatus);
        data.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return data;
    }

    /**
     * 华为云 device_id 映射为本系统 deviceCode
     *
     * 映射规则：
     *   6a43868f7f2e6c302f80868e_2020111409 → human_001
     *
     * 可扩展为数据库映射表
     */
    private String mapDeviceId(String huaweiDeviceId) {
        if (huaweiDeviceId == null) return "human_001";

        // 精确映射
        if (huaweiDeviceId.contains("2020111409")) {
            System.out.println("device_id 映射: " + huaweiDeviceId + " → human_001");
            return "human_001";
        }

        // 默认兜底
        System.out.println("device_id 映射: " + huaweiDeviceId + " → human_001（默认）");
        return "human_001";
    }
}
