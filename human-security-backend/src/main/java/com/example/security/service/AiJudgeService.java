package com.example.security.service;

import com.example.security.dto.AiJudgeResult;
import com.example.security.entity.AlarmRecord;
import com.example.security.entity.DeviceStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiJudgeService {

    @Autowired
    private AlarmRecordService alarmRecordService;

    @Autowired
    private DeviceStatusService deviceStatusService;

    /**
     * Comprehensive AI risk assessment
     * Uses rule-based logic; LLM is reserved but not required
     */
    public AiJudgeResult judge(Long alarmId) {
        // 1. Collect data
        List<AlarmRecord> allAlarms = alarmRecordService.list(null, null);
        List<DeviceStatus> allDevices = deviceStatusService.getAll();

        // 2. Analyze current state
        List<AlarmRecord> unhandledAlarms = new ArrayList<>();
        List<AlarmRecord> highAlarms = new ArrayList<>();
        List<AlarmRecord> mediumAlarms = new ArrayList<>();

        for (AlarmRecord a : allAlarms) {
            Integer hs = a.getHandleStatus();
            if (hs == null || hs == 0) { // UNHANDLED
                unhandledAlarms.add(a);
                String level = a.getAlarmLevel();
                if (level != null) {
                    if (level.equalsIgnoreCase("HIGH")) highAlarms.add(a);
                    else if (level.equalsIgnoreCase("MEDIUM")) mediumAlarms.add(a);
                }
            }
        }

        List<DeviceStatus> offlineDevices = new ArrayList<>();
        List<DeviceStatus> lowBatteryDevices = new ArrayList<>();
        for (DeviceStatus d : allDevices) {
            if (d.getOnline() != null && d.getOnline() == 0) offlineDevices.add(d);
            if (d.getBattery() != null && d.getBattery() < 20) lowBatteryDevices.add(d);
        }

        // 3. If specific alarm requested, analyze it
        AlarmRecord targetAlarm = null;
        if (alarmId != null) {
            for (AlarmRecord a : allAlarms) {
                if (a.getId().equals(alarmId)) { targetAlarm = a; break; }
            }
        }

        // 4. Try LLM first if configured
        AiJudgeResult llmResult = tryLLM(targetAlarm, unhandledAlarms, offlineDevices, allDevices);
        if (llmResult != null) return llmResult;

        // 5. Rule-based judgment (fallback)
        AiJudgeResult ruleResult = ruleJudge(targetAlarm, unhandledAlarms, highAlarms, mediumAlarms,
                         offlineDevices, lowBatteryDevices, allDevices);
        ruleResult.setFallback(true);
        String apiKey = System.getenv().getOrDefault("AI_API_KEY", "");
        ruleResult.setErrorType((apiKey != null && !apiKey.trim().isEmpty()) ? "LLM_REQUEST_FAILED" : "NO_API_KEY");
        return ruleResult;
    }

    /**
     * Rule-based risk assessment engine
     */
    private AiJudgeResult ruleJudge(AlarmRecord targetAlarm,
                                     List<AlarmRecord> unhandledAlarms,
                                     List<AlarmRecord> highAlarms,
                                     List<AlarmRecord> mediumAlarms,
                                     List<DeviceStatus> offlineDevices,
                                     List<DeviceStatus> lowBatteryDevices,
                                     List<DeviceStatus> allDevices) {

        boolean hasHighUnhandled = !highAlarms.isEmpty();
        boolean hasMediumUnhandled = !mediumAlarms.isEmpty();
        boolean hasOffline = !offlineDevices.isEmpty();
        boolean hasLowBattery = !lowBatteryDevices.isEmpty();

        // Rule 1: HIGH unhandled + device offline → CRITICAL
        if (hasHighUnhandled && hasOffline) {
            return buildResult("CRITICAL", "MULTI_RISK",
                "当前存在高危未处理告警且有设备离线，安防风险严重",
                "系统检测到 " + highAlarms.size() + " 条高危未处理告警，同时有 " +
                offlineDevices.size() + " 台设备离线。高危告警叠加监控盲区，风险极高。",
                Arrays.asList(
                    "立即查看告警详情，确认现场情况",
                    "检查离线设备电源和网络连接",
                    "生成求助信息并通知保安或紧急联系人",
                    "不建议直接标记已处理，需人工确认后操作"
                ), 0.92, "RULE");
        }

        // Rule 2: HIGH unhandled → HIGH
        if (hasHighUnhandled) {
            AlarmRecord first = highAlarms.get(0);
            return buildResult("HIGH", "INTRUSION",
                "当前存在高危未处理告警",
                "系统检测到 " + highAlarms.size() + " 条高危未处理告警（最新：" +
                (first.getAlarmContent() != null ? first.getAlarmContent() : first.getDeviceName()) +
                "），需要立即关注。",
                Arrays.asList(
                    "查看告警详情了解具体情况",
                    "确认现场是否真的存在安全威胁",
                    "生成求助信息发送给相关人员",
                    "必要时发送站内消息通知其他用户"
                ), 0.88, "RULE");
        }

        // Rule 3: MEDIUM unhandled → MEDIUM
        if (hasMediumUnhandled) {
            return buildResult("MEDIUM", "INTRUSION",
                "当前存在中等级未处理告警",
                "系统检测到 " + mediumAlarms.size() + " 条中等级未处理告警，虽非紧急但仍需关注。",
                Arrays.asList(
                    "查看告警详情，结合设备位置判断风险",
                    "持续观察，确认是否持续触发",
                    "如确认无风险，可标记已处理"
                ), 0.80, "RULE");
        }

        // Rule 4: Device offline → MEDIUM
        if (hasOffline) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, offlineDevices.size()); i++) {
                if (i > 0) sb.append("、");
                sb.append(offlineDevices.get(i).getDeviceName());
            }
            return buildResult("MEDIUM", "DEVICE_OFFLINE",
                "当前存在离线设备",
                "检测到 " + offlineDevices.size() + " 台设备离线（" + sb.toString() +
                "等），这些设备无法正常上报数据，存在监控盲区。",
                Arrays.asList(
                    "检查离线设备电源是否正常",
                    "检查设备网络连接和 MQTT 状态",
                    "确认设备最后上报时间",
                    "如设备已损坏需及时更换"
                ), 0.85, "RULE");
        }

        // Rule 5: Low battery → MEDIUM
        if (hasLowBattery) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, lowBatteryDevices.size()); i++) {
                if (i > 0) sb.append("、");
                sb.append(lowBatteryDevices.get(i).getDeviceName())
                  .append("(").append(lowBatteryDevices.get(i).getBattery()).append("%)");
            }
            return buildResult("MEDIUM", "LOW_BATTERY",
                "当前存在低电量设备",
                "检测到 " + lowBatteryDevices.size() + " 台设备电量低于 20%（" +
                sb.toString() + "），可能随时断电失去监测能力。",
                Arrays.asList(
                    "尽快为低电量设备更换电池或充电",
                    "避免因断电导致监控盲区",
                    "建议建立定期电量检查机制"
                ), 0.82, "RULE");
        }

        // Rule 6: All normal → LOW
        int totalDevices = allDevices.size();
        int onlineCount = 0;
        int defenseCount = 0;
        for (DeviceStatus d : allDevices) {
            if (d.getOnline() != null && d.getOnline() == 1) onlineCount++;
            if (d.getDefense() != null && d.getDefense() == 1) defenseCount++;
        }
        return buildResult("LOW", "NORMAL",
            "当前安防状态正常",
            "当前 " + totalDevices + " 台设备中 " + onlineCount + " 台在线，" +
            defenseCount + " 台已布防，无未处理告警。系统运行状态良好。",
            Arrays.asList(
                "继续保持设备在线和布防策略",
                "定期检查设备电量和网络状态",
                "保持当前安防策略有效运行"
            ), 0.95, "RULE");
    }

    /**
     * Build a standardized AiJudgeResult
     */
    private AiJudgeResult buildResult(String riskLevel, String eventType, String summary,
                                       String reason, List<String> suggestions,
                                       double confidence, String source) {
        AiJudgeResult r = new AiJudgeResult();
        r.setRiskLevel(riskLevel);
        r.setEventType(eventType);
        r.setSummary(summary);
        r.setReason(reason);
        r.setSuggestions(suggestions);
        r.setNeedHumanConfirm(!"LOW".equals(riskLevel));
        r.setConfidence(confidence);
        r.setSource(source);
        return r;
    }

    private static final String DEEPSEEK_DEFAULT_URL = "https://api.deepseek.com";
    private static final String DEEPSEEK_DEFAULT_MODEL = "deepseek-v4-pro";
    private static final int LLM_TIMEOUT_MS = 25000;

    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 尝试调用 DeepSeek 进行风险研判——失败返回 null，回退到规则引擎
     */
    private AiJudgeResult tryLLM(AlarmRecord targetAlarm,
                                  List<AlarmRecord> unhandledAlarms,
                                  List<DeviceStatus> offlineDevices,
                                  List<DeviceStatus> allDevices) {
        String apiKey = System.getenv().getOrDefault("AI_API_KEY", "");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null; // No LLM configured, use rules
        }

        String apiUrl = System.getenv("AI_API_URL");
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            apiUrl = DEEPSEEK_DEFAULT_URL;
        }
        apiUrl = apiUrl.replaceAll("/+$", "");

        String model = System.getenv("AI_MODEL");
        if (model == null || model.trim().isEmpty()) {
            model = DEEPSEEK_DEFAULT_MODEL;
        }

        try {
            // Build context
            StringBuilder ctx = new StringBuilder();
            ctx.append("请对当前安防状态进行风险研判。\n\n");

            if (targetAlarm != null) {
                ctx.append("目标告警：").append(targetAlarm.getDeviceName())
                   .append(" - ").append(targetAlarm.getAlarmContent())
                   .append("（").append(targetAlarm.getAlarmLevelName()).append("）\n\n");
            }

            ctx.append("未处理告警：").append(unhandledAlarms.size()).append(" 条\n");
            int idx = 0;
            for (AlarmRecord a : unhandledAlarms) {
                if (idx++ >= 10) break;
                ctx.append("- ").append(a.getAlarmLevelName()).append("：").append(a.getDeviceName())
                   .append("，").append(a.getAlarmContent()).append("\n");
            }

            ctx.append("\n离线设备：").append(offlineDevices.size()).append(" 台\n");
            for (DeviceStatus d : offlineDevices) {
                ctx.append("- ").append(d.getDeviceName()).append("（").append(d.getLocation()).append("）\n");
            }

            int onlineCount = 0;
            for (DeviceStatus d : allDevices) {
                if (d.getOnline() != null && d.getOnline() == 1) onlineCount++;
            }
            ctx.append("\n设备总数：").append(allDevices.size())
               .append("，在线：").append(onlineCount).append("\n");

            ctx.append("\n请返回 JSON（只返回 JSON）：\n")
               .append("{\"riskLevel\":\"LOW/MEDIUM/HIGH/CRITICAL\",")
               .append("\"eventType\":\"NORMAL/DEVICE_OFFLINE/LOW_BATTERY/INTRUSION/MULTI_RISK\",")
               .append("\"summary\":\"摘要\",\"reason\":\"原因\",")
               .append("\"suggestions\":[\"建议\"],")
               .append("\"needHumanConfirm\":true,\"confidence\":0.9,\"source\":\"LLM\"}");

            String systemPrompt = "你是智慧安防系统的 AI 风险研判专家。你只能输出 JSON。";

            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model", model);
            reqBody.put("messages", Arrays.asList(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", ctx.toString())
            ));
            reqBody.put("stream", false);
            reqBody.put("temperature", 0.2);
            Map<String, String> respFmt = new LinkedHashMap<>();
            respFmt.put("type", "json_object");
            reqBody.put("response_format", respFmt);

            String reqJson = objectMapper.writeValueAsString(reqBody);

            java.net.http.HttpRequest httpReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(apiUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(java.time.Duration.ofMillis(LLM_TIMEOUT_MS))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(reqJson))
                    .build();

            java.net.http.HttpResponse<String> httpResp = httpClient.send(httpReq,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (httpResp.statusCode() != 200) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = objectMapper.readValue(httpResp.body(), Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
            if (choices == null || choices.isEmpty()) return null;

            String content = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
            if (content == null || content.trim().isEmpty()) return null;

            content = content.trim();
            if (content.startsWith("```")) {
                int start = content.indexOf("\n");
                int end = content.lastIndexOf("```");
                if (start >= 0 && end > start) content = content.substring(start + 1, end).trim();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> raw = objectMapper.readValue(content, Map.class);

            AiJudgeResult r = new AiJudgeResult();
            String rl = raw.get("riskLevel") != null ? String.valueOf(raw.get("riskLevel")) : "MEDIUM";
            if (!Arrays.asList("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(rl)) rl = "MEDIUM";
            r.setRiskLevel(rl);
            r.setEventType(raw.get("eventType") != null ? String.valueOf(raw.get("eventType")) : "UNKNOWN");
            r.setSummary(raw.get("summary") != null ? String.valueOf(raw.get("summary")) : "");
            r.setReason(raw.get("reason") != null ? String.valueOf(raw.get("reason")) : "");

            List<String> suggestions = new ArrayList<>();
            Object sugObj = raw.get("suggestions");
            if (sugObj instanceof List) {
                for (Object s : (List<?>) sugObj) suggestions.add(String.valueOf(s));
            }
            r.setSuggestions(suggestions);

            r.setNeedHumanConfirm(!"LOW".equals(rl));
            r.setConfidence(raw.get("confidence") instanceof Number
                    ? ((Number) raw.get("confidence")).doubleValue() : 0.8);
            r.setSource("LLM");
            r.setModel(model);
            r.setFallback(false);

            System.out.println("[AiJudgeService] DeepSeek 研判成功, risk=" + rl + ", model=" + model);
            return r;

        } catch (Exception e) {
            System.err.println("[AiJudgeService] DeepSeek 调用失败: " + e.getMessage());
            return null;
        }
    }
}
