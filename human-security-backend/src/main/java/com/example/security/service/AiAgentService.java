package com.example.security.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.security.dto.ActionDraft;
import com.example.security.dto.AiAgentResponse;
import com.example.security.entity.AlarmRecord;
import com.example.security.entity.DeviceStatus;
import com.example.security.entity.Knowledge;
import com.example.security.mapper.ChatRecordMapper;
import com.example.security.mapper.KnowledgeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
public class AiAgentService {

    @Autowired
    private DeviceStatusService deviceStatusService;

    @Autowired
    private AlarmRecordService alarmRecordService;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private ChatRecordMapper chatRecordMapper;

    @Autowired(required = false)
    private PirService pirService;

    private PirService getPirService() {
        return pirService;
    }

    // ==================== 启动诊断 ====================

    @PostConstruct
    public void init() {
        boolean keyPresent = isApiKeyConfigured();
        System.out.println("[AiAgentService] ========================================");
        System.out.println("[AiAgentService] AI_API_KEY_PRESENT=" + keyPresent);
        if (keyPresent) {
            String model = System.getenv("AI_MODEL");
            String url = System.getenv("AI_API_URL");
            System.out.println("[AiAgentService] AI_MODEL=" + (model != null && !model.trim().isEmpty() ? model : DEEPSEEK_DEFAULT_MODEL));
            System.out.println("[AiAgentService] AI_API_URL=" + (url != null && !url.trim().isEmpty() ? url : DEEPSEEK_DEFAULT_URL));
        } else {
            System.out.println("[AiAgentService] 未配置 AI_API_KEY，所有问答走本地规则引擎");
        }
        System.out.println("[AiAgentService] ========================================");
    }

    // ==================== 主入口 ====================

    public AiAgentResponse chat(Long userId, String message) {
        return chat(userId, message, null, null);
    }

    /**
     * 带前端实时上下文的主入口
     * @param systemContext 前端传来的设备/告警/PIR/人脸实时数据（可选）
     * @param scene 使用场景（可选）
     */
    public AiAgentResponse chat(Long userId, String message,
                                 Map<String, Object> systemContext,
                                 String scene) {
        if (message == null || message.trim().isEmpty()) {
            return buildEmptyResponse();
        }

        // 1. 意图识别
        String intent = recognizeIntent(message);

        // 2. 尝试 LLM (DeepSeek)
        AiAgentResponse llmResult = tryLLM(userId, message, intent, systemContext, scene);
        if (llmResult != null) {
            // LLM 调用成功，保存记录
            saveRecord(userId, message, llmResult.getAnswer(), intent);
            return llmResult;
        }

        // 3. 规则智能体兜底
        AiAgentResponse result = ruleAgent(userId, message, intent);
        // 标记为兜底
        result.setFallback(true);
        if (result.getErrorType() == null) {
            result.setErrorType(isApiKeyConfigured() ? "LLM_REQUEST_FAILED" : "NO_API_KEY");
        }

        // 4. 保存聊天记录
        saveRecord(userId, message, result.getAnswer(), intent);

        return result;
    }

    // ==================== 意图识别 ====================

    private String recognizeIntent(String message) {
        String msg = message.trim();

        // ── 0. 帮助/使用说明 → 欢迎语 ──
        if (containsAny(msg, "你能做什么", "你可以做什么", "你会什么", "使用说明",
                "怎么用", "帮助", "功能介绍", "有哪些功能", "你能干什么")) {
            return "HELP_INTRO";
        }

        // ── 1. 知识问答优先 — "是什么/怎么办/如何/什么意思/为什么" ──
        //    必须在 PIR_QUERY / DEVICE_QUERY 之前判断，避免 "PIR是什么" 被误判为 PIR 状态查询
        if (containsAny(msg, "是什么", "是什么意思", "怎么办", "如何", "为什么",
                "什么意思", "啥意思", "怎么理解", "是什么原理", "解释")) {
            return "KNOWLEDGE_QA";
        }

        // ── 2. 安全状态判断（最高优先级操作类）──
        if (containsAny(msg, "安全吗", "安不安全", "有没有危险", "家里安全", "是否安全",
                "现在安全", "当前安全", "安全状态", "目前安全", "安全情况", "怎么不安全")) {
            return "SECURITY_STATUS";
        }

        // ── 3. PIR 人体感应查询（操作类，不含"是什么"）──
        if (containsAny(msg, "人体感应", "人体检测", "人体活动", "有人经过",
                "有没有人", "今天有没有人", "检测到人", "人感", "红外感应",
                "查看人体", "感应状态", "感应记录", "人体传感器",
                "PIR", "今天检测", "今天感应", "今天有人")) {
            return "PIR_QUERY";
        }

        // ── 4. 设备查询 ──
        if (containsAny(msg, "设备是否在线", "在线吗", "在线状态", "是否在线", "离线吗",
                "是否离线", "电量", "是否布防", "布防状态", "是否布防", "布防吗",
                "传感器电量", "传感器状态", "怎么离线", "设备状态", "当前设备",
                "查看设备")) {
            return "DEVICE_QUERY";
        }

        // ── 5. 告警查询 ──
        if (containsAny(msg, "有没有告警", "最近告警", "最新告警", "有告警吗",
                "查看告警", "告警信息", "告警列表", "告警记录",
                "有没有入侵", "有人闯入", "是否有陌生人", "陌生人告警",
                "当前告警", "未处理告警", "告警多不多", "告警多吗",
                "报警多不多", "警告多不多", "异常多不多",
                "今天告警", "今日告警", "今天报警", "今天警告",
                "本周告警", "一周告警", "这周告警", "最近七天",
                "今天有多少", "一周有多少", "有多少条", "有几条")) {
            return "ALARM_QUERY";
        }

        // ── 6. 求助建议 ──
        if (containsAny(msg, "怎么办", "怎么处理", "如何应对", "怎么解决",
                "通知保安", "帮我生成", "生成一条", "生成求助",
                "求助", "帮我通知", "告诉保安", "发送消息")) {
            return "HELP_ADVICE";
        }

        // ── 7. 告警弱匹配 ──
        if (containsAny(msg, "告警", "报警", "警告", "预警", "警报", "异常", "入侵", "陌生人", "闯入", "未处理")) {
            return "ALARM_QUERY";
        }

        // ── 8. 设备弱匹配 ──
        if (containsAny(msg, "设备", "在线", "离线", "布防", "撤防", "传感器", "摄像头", "门磁")) {
            return "DEVICE_QUERY";
        }

        // ── 9. 知识库弱匹配 ──
        if (containsAny(msg, "布防", "短信", "MQTT", "物联网", "安防")) {
            return "KNOWLEDGE_QA";
        }

        return "UNKNOWN";
    }

    private boolean containsAny(String msg, String... keywords) {
        for (String kw : keywords) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }

    // ==================== 规则智能体核心 ====================

    private AiAgentResponse ruleAgent(Long userId, String message, String intent) {
        switch (intent) {
            case "SECURITY_STATUS": return handleSecurityStatus(message);
            case "PIR_QUERY":       return handlePirQuery(message);
            case "DEVICE_QUERY":    return handleDeviceQuery(message);
            case "ALARM_QUERY":     return handleAlarmQuery(message);
            case "HELP_ADVICE":     return handleHelpAdvice(message);
            case "KNOWLEDGE_QA":    return handleKnowledgeQa(message);
            case "HELP_INTRO":      return handleHelpIntro();
            default:                return handleUnknown(message);
        }
    }

    // ==================== 安全状态 ====================

    private AiAgentResponse handleSecurityStatus(String message) {
        AiAgentResponse r = new AiAgentResponse();
        r.setIntent("SECURITY_STATUS");
        r.setSource("RULE");
        r.setUsedTools(Arrays.asList("device_status", "alarm_record"));

        List<DeviceStatus> devices = deviceStatusService.getAll();
        List<AlarmRecord> allAlarms = alarmRecordService.list(null, null);

        // 分析
        List<AlarmRecord> unhandled = new ArrayList<>();
        List<AlarmRecord> highAlarms = new ArrayList<>();
        for (AlarmRecord a : allAlarms) {
            if (a.getHandleStatus() == null || a.getHandleStatus() == 0) {
                unhandled.add(a);
                if ("HIGH".equalsIgnoreCase(a.getAlarmLevel())) highAlarms.add(a);
            }
        }

        List<DeviceStatus> offlineDevices = new ArrayList<>();
        List<DeviceStatus> lowBatteryDevices = new ArrayList<>();
        int onlineCount = 0, defenseCount = 0;
        for (DeviceStatus d : devices) {
            if (d.getOnline() != null && d.getOnline() == 1) onlineCount++;
            else offlineDevices.add(d);
            if (d.getDefense() != null && d.getDefense() == 1) defenseCount++;
            if (d.getBattery() != null && d.getBattery() < 20) lowBatteryDevices.add(d);
        }

        List<String> evidence = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        StringBuilder answer = new StringBuilder();

        // 判断风险等级
        if (!highAlarms.isEmpty() && !offlineDevices.isEmpty()) {
            r.setRiskLevel("CRITICAL");
            answer.append("⚠️ 当前安防状态：**严重风险**，不建议认为安全。\n\n");
            answer.append("检测到 ").append(highAlarms.size()).append(" 条高危未处理告警，同时有 ")
                  .append(offlineDevices.size()).append(" 台设备离线。高危告警叠加监控盲区，安防形势严峻。\n");
        } else if (!highAlarms.isEmpty()) {
            r.setRiskLevel("HIGH");
            answer.append("⚠️ 当前安防状态：**高风险**，需要立即关注。\n\n");
            answer.append("检测到 ").append(highAlarms.size()).append(" 条高危未处理告警。\n");
        } else if (!unhandled.isEmpty()) {
            r.setRiskLevel("MEDIUM");
            answer.append("⚠️ 当前安防状态：**中等风险**，存在未处理告警。\n\n");
            answer.append("检测到 ").append(unhandled.size()).append(" 条未处理告警。\n");
        } else if (!offlineDevices.isEmpty()) {
            r.setRiskLevel("MEDIUM");
            answer.append("⚠️ 当前安防状态：**中等风险**，存在离线设备。\n\n");
            answer.append("检测到 ").append(offlineDevices.size()).append(" 台设备离线，存在监控盲区。\n");
        } else if (!lowBatteryDevices.isEmpty()) {
            r.setRiskLevel("MEDIUM");
            answer.append("⚠️ 当前安防状态：**中等风险**，存在低电量设备。\n\n");
        } else {
            r.setRiskLevel("LOW");
            answer.append("✅ 当前安防状态：**安全**，系统运行正常。\n\n");
            answer.append(devices.size()).append(" 台设备中 ").append(onlineCount).append(" 台在线，")
                  .append(defenseCount).append(" 台已布防，无未处理告警。\n");
        }

        // 证据
        for (AlarmRecord a : highAlarms) {
            String time = a.getAlarmTime() != null ? a.getAlarmTime().toString().replace("T", " ") : "未知";
            evidence.add(a.getDeviceName() + "：" + a.getAlarmContent() + "（" + a.getAlarmLevelName() + "，" + time + "）");
        }
        for (DeviceStatus d : offlineDevices) {
            evidence.add(d.getDeviceName() + "（" + d.getLocation() + "）当前离线");
        }
        for (DeviceStatus d : lowBatteryDevices) {
            evidence.add(d.getDeviceName() + "（" + d.getLocation() + "）电量为 " + d.getBattery() + "%");
        }
        if (evidence.isEmpty()) {
            evidence.add("所有设备正常运行，无异常告警");
        }

        // 建议
        if (!highAlarms.isEmpty() || !unhandled.isEmpty()) {
            suggestions.add("立即查看告警详情，确认现场情况");
            suggestions.add("生成求助信息并通知保安或紧急联系人");
        }
        if (!offlineDevices.isEmpty()) {
            suggestions.add("检查离线设备电源和网络连接");
            suggestions.add("确认设备最后上报时间，排查 MQTT 服务状态");
        }
        if (!lowBatteryDevices.isEmpty()) {
            suggestions.add("尽快为低电量设备更换电池或充电");
        }
        if (r.getRiskLevel().equals("LOW")) {
            suggestions.add("继续保持设备在线和布防策略");
            suggestions.add("定期检查设备电量和网络状态");
        }

        // 草稿
        if (!r.getRiskLevel().equals("LOW")) {
            String draftContent = buildHelpDraftContent(devices, unhandled, highAlarms, offlineDevices);
            r.setActionDraft(new ActionDraft("HELP_MESSAGE",
                    "智慧安防安全提醒 - 当前存在安全风险",
                    draftContent));
        }

        r.setAnswer(answer.toString().trim());
        r.setEvidence(evidence);
        r.setSuggestions(suggestions);
        return r;
    }

    // ==================== 设备查询 ====================

    private AiAgentResponse handleDeviceQuery(String message) {
        AiAgentResponse r = new AiAgentResponse();
        r.setIntent("DEVICE_QUERY");
        r.setSource("RULE");
        r.setUsedTools(Collections.singletonList("device_status"));

        List<DeviceStatus> devices = deviceStatusService.getAll();
        List<String> evidence = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        StringBuilder answer = new StringBuilder();
        answer.append("📡 当前设备状态如下：\n\n");

        int onlineCount = 0, defenseCount = 0;
        boolean hasOffline = false, hasLowBattery = false;

        for (int i = 0; i < devices.size(); i++) {
            DeviceStatus d = devices.get(i);
            String onlineText = (d.getOnline() != null && d.getOnline() == 1) ? "✅ 在线" : "❌ 离线";
            String defenseText = (d.getDefense() != null && d.getDefense() == 1) ? "已布防" : "已撤防";
            String batteryText = d.getBattery() != null ? d.getBattery() + "%" : "N/A";

            answer.append(i + 1).append(". ").append(d.getDeviceName())
                  .append("（").append(d.getLocation()).append("）\n")
                  .append("   状态：").append(onlineText)
                  .append(" | 电量：").append(batteryText)
                  .append(" | ").append(defenseText).append("\n");

            if (d.getOnline() != null && d.getOnline() == 1) onlineCount++;
            else { hasOffline = true; evidence.add(d.getDeviceName() + "（" + d.getLocation() + "）当前离线"); }
            if (d.getDefense() != null && d.getDefense() == 1) defenseCount++;
            if (d.getBattery() != null && d.getBattery() < 20) {
                hasLowBattery = true;
                evidence.add(d.getDeviceName() + "（" + d.getLocation() + "）电量仅 " + d.getBattery() + "%");
            }
        }

        // 综合判断
        answer.append("\n📊 汇总：").append(onlineCount).append("/").append(devices.size())
              .append(" 台在线，").append(defenseCount).append(" 台已布防。");

        if (hasOffline) {
            r.setRiskLevel("MEDIUM");
            answer.append("\n⚠️ 存在离线设备，有监控盲区！");
            suggestions.add("检查离线设备的电源和网络连接");
            suggestions.add("确认 MQTT 服务是否正常运行");
        }
        if (hasLowBattery) {
            if (r.getRiskLevel() == null) r.setRiskLevel("MEDIUM");
            answer.append("\n⚠️ 存在低电量设备，可能随时断电！");
            suggestions.add("尽快为低电量设备更换电池或充电");
        }
        if (!hasOffline && !hasLowBattery) {
            r.setRiskLevel("LOW");
            answer.append("\n✅ 所有设备运行正常。");
            suggestions.add("继续保持当前设备运行状态");
        }

        r.setAnswer(answer.toString().trim());
        r.setEvidence(evidence.isEmpty() ? Collections.singletonList("所有设备状态正常") : evidence);
        r.setSuggestions(suggestions);
        return r;
    }

    // ==================== 告警查询 ====================

    private AiAgentResponse handleAlarmQuery(String message) {
        AiAgentResponse r = new AiAgentResponse();
        r.setIntent("ALARM_QUERY");
        r.setSource("RULE");
        r.setUsedTools(Arrays.asList("alarm_record", "device_status"));

        List<AlarmRecord> unhandled = alarmRecordService.getUnhandled();
        List<AlarmRecord> allAlarms = alarmRecordService.list(null, null);
        List<String> evidence = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        StringBuilder answer = new StringBuilder();

        int unhandledCount = unhandled.size();
        long highCount = unhandled.stream().filter(a -> "HIGH".equalsIgnoreCase(a.getAlarmLevel())).count();

        if (unhandled.isEmpty()) {
            r.setRiskLevel("LOW");
            answer.append("✅ 当前暂无未处理告警，系统处于正常监测状态。\n");
            if (!allAlarms.isEmpty()) {
                answer.append("最近共有 ").append(allAlarms.size()).append(" 条告警记录，均已处理。\n");
            }
        } else if (highCount > 0) {
            r.setRiskLevel("HIGH");
            answer.append("🚨 当前存在 **").append(unhandledCount).append("** 条未处理告警，其中 **")
                  .append(highCount).append("** 条为高危告警，需要立即关注！\n\n");
        } else {
            r.setRiskLevel("MEDIUM");
            answer.append("⚠️ 当前存在 **").append(unhandledCount).append("** 条未处理告警，请及时查看处理。\n\n");
        }

        if (!unhandled.isEmpty()) {
            answer.append("未处理告警列表：\n");
            for (int i = 0; i < Math.min(5, unhandled.size()); i++) {
                AlarmRecord a = unhandled.get(i);
                String time = a.getAlarmTime() != null ? a.getAlarmTime().toString().replace("T", " ") : "未知";
                String levelEmoji = "HIGH".equalsIgnoreCase(a.getAlarmLevel()) ? "🔴" :
                                    "MEDIUM".equalsIgnoreCase(a.getAlarmLevel()) ? "🟠" : "🟡";
                answer.append(levelEmoji).append(" ").append(i + 1).append(". ")
                      .append(a.getDeviceName()).append("：").append(a.getAlarmContent())
                      .append("（").append(a.getAlarmLevelName()).append("，" ).append(time).append("）\n");

                evidence.add(a.getDeviceName() + "检测到" + a.getAlarmContent() + "（" + a.getAlarmLevelName() + "，未处理）");
            }
            if (unhandled.size() > 5) {
                answer.append("... 还有 ").append(unhandled.size() - 5).append(" 条未处理告警\n");
            }
        }

        suggestions.add("查看告警详情，确认现场情况");
        suggestions.add("如确认为真实告警，请及时处理");
        if (highCount > 0) {
            suggestions.add("生成求助信息并通知相关人员");
            r.setActionDraft(new ActionDraft("HELP_MESSAGE",
                    "智慧安防高危告警提醒",
                    buildAlarmDraftContent(unhandled)));
        }
        suggestions.add("如确认为误报，可标记为误报");

        r.setAnswer(answer.toString().trim());
        r.setEvidence(evidence.isEmpty() ? Collections.singletonList("暂无未处理告警") : evidence);
        r.setSuggestions(suggestions);
        return r;
    }

    // ==================== 求助建议 ====================

    private AiAgentResponse handleHelpAdvice(String message) {
        AiAgentResponse r = new AiAgentResponse();
        r.setIntent("HELP_ADVICE");
        r.setSource("RULE");
        r.setUsedTools(Arrays.asList("kb_knowledge", "alarm_record", "device_status"));

        List<String> evidence = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        StringBuilder answer = new StringBuilder();

        // 查询知识库
        String kbAnswer = matchKnowledge(message);
        // 查询当前告警
        List<AlarmRecord> unhandled = alarmRecordService.getUnhandled();
        List<DeviceStatus> devices = deviceStatusService.getAll();

        // 如果问的是"生成消息"/"通知保安"
        if (message.contains("生成") || message.contains("通知保安") || message.contains("发送消息")
                || message.contains("告诉保安") || message.contains("帮我通知")) {
            answer.append("📝 已为你生成消息草稿：\n\n");

            String draftTitle, draftType;
            if (message.contains("保安")) {
                draftTitle = "智慧安防通知 - 请保安协助查看";
                draftType = "ALARM_MESSAGE";
            } else {
                draftTitle = "智慧安防求助提醒";
                draftType = "HELP_MESSAGE";
            }

            StringBuilder draftContent = new StringBuilder();
            draftContent.append("【智慧安防系统提醒】\n\n");

            if (!unhandled.isEmpty()) {
                draftContent.append("当前存在未处理告警：\n");
                for (int i = 0; i < Math.min(3, unhandled.size()); i++) {
                    AlarmRecord a = unhandled.get(i);
                    draftContent.append("• ").append(a.getDeviceName()).append("：")
                               .append(a.getAlarmContent()).append("\n");
                }
            }

            // 离线设备
            long offlineCount = devices.stream().filter(d -> d.getOnline() != null && d.getOnline() == 0).count();
            if (offlineCount > 0) {
                draftContent.append("\n离线设备：\n");
                devices.stream().filter(d -> d.getOnline() != null && d.getOnline() == 0)
                       .forEach(d -> draftContent.append("• ").append(d.getDeviceName())
                               .append("（").append(d.getLocation()).append("）\n"));
            }

            draftContent.append("\n请尽快确认现场情况。如有异常，请及时联系相关人员处理。");
            draftContent.append("\n\n—— 智慧安防系统");

            answer.append("标题：").append(draftTitle).append("\n");
            answer.append("内容：\n").append(draftContent.toString());

            r.setActionDraft(new ActionDraft(draftType, draftTitle, draftContent.toString()));
            r.setRiskLevel(unhandled.isEmpty() ? "LOW" : "MEDIUM");
        } else {
            // 一般求助建议：知识库 + 当前状态
            if (kbAnswer != null && !kbAnswer.isEmpty()) {
                answer.append("📋 知识库建议：\n").append(kbAnswer).append("\n\n");
                evidence.add("知识库匹配到相关建议");
            }

            // 附加当前状态
            if (!unhandled.isEmpty()) {
                r.setRiskLevel("MEDIUM");
                answer.append("⚠️ 当前存在未处理告警，请注意：\n");
                for (int i = 0; i < Math.min(3, unhandled.size()); i++) {
                    AlarmRecord a = unhandled.get(i);
                    answer.append("• ").append(a.getDeviceName()).append("：")
                          .append(a.getAlarmContent()).append("\n");
                    evidence.add("当前告警：" + a.getDeviceName() + " - " + a.getAlarmContent());
                }
            } else {
                r.setRiskLevel("LOW");
                answer.append("✅ 当前暂无未处理告警。\n");
            }
        }

        suggestions.add("查看告警详情，确认现场情况");
        suggestions.add("通过紧急求助页面生成并发送求助信息");
        suggestions.add("通过消息中心通知其他用户");
        suggestions.add("如确认为紧急情况，请直接联系小区保安");

        r.setAnswer(answer.toString().trim());
        r.setEvidence(evidence.isEmpty() ? Collections.singletonList("根据知识库和当前安防状态综合判断") : evidence);
        r.setSuggestions(suggestions);
        return r;
    }

    // ==================== 知识问答 ====================

    private AiAgentResponse handleKnowledgeQa(String message) {
        AiAgentResponse r = new AiAgentResponse();
        r.setIntent("KNOWLEDGE_QA");
        r.setSource("RULE");
        r.setRiskLevel("LOW");
        r.setUsedTools(Collections.singletonList("kb_knowledge"));

        // 先尝试本地知识库匹配
        String localAnswer = matchKnowledge(message);

        // 如果本地知识库有匹配，直接用
        if (localAnswer != null && !localAnswer.trim().isEmpty()) {
            r.setAnswer(localAnswer);
            r.setEvidence(Collections.singletonList("本地知识库匹配"));
            r.setSuggestions(Arrays.asList("追问「请详细解释」获取更多信息", "问「我现在安全吗」获取实时状态"));
            return r;
        }

        // 本地知识库无匹配 → 根据常见问题生成本地解释
        String builtinAnswer = buildBuiltinKnowledgeAnswer(message);
        if (builtinAnswer != null) {
            r.setAnswer(builtinAnswer);
            r.setEvidence(Collections.singletonList("内置安防知识库"));
            r.setSuggestions(Arrays.asList("追问「请详细解释」获取更多信息", "问「我现在安全吗」获取实时状态"));
            return r;
        }

        // 完全无法匹配 → 返回通用引导（但不是欢迎语）
        r.setAnswer("关于「" + message + "」，我暂时没有找到相关知识。\n\n"
                + "你可以试试问：\n"
                + "• PIR 人体感应是什么原理？\n"
                + "• 布防和撤防有什么区别？\n"
                + "• 摄像头离线怎么办？\n"
                + "• 如何提高家庭安防水平？");
        r.setEvidence(Collections.singletonList("本地知识库无匹配"));
        r.setSuggestions(Arrays.asList("尝试换个问法", "问「我现在安全吗」获取实时状态", "说「帮助」查看功能介绍"));
        return r;
    }

    /**
     * 内置安防知识库 — 常见问题的本地解释
     * 当 DeepSeek 不可用时使用，确保用户不会收到空泛的"我不理解"
     */
    private String buildBuiltinKnowledgeAnswer(String message) {
        String msg = message.toLowerCase();

        // PIR 相关
        if (msg.contains("pir")) {
            return "PIR 是被动红外传感器的缩写。它通过检测人体发出的红外线来判断是否有人经过，"
                    + "是安防系统中最常用的传感器之一。\n\n"
                    + "PIR 传感器的优点是功耗低、成本低、隐蔽性好，缺点是只能检测移动的人体，"
                    + "静止不动的人可能检测不到。\n\n"
                    + "在本系统中，PIR 传感器配合布防策略使用——"
                    + "只有布防状态下检测到人体活动才会触发告警。";
        }

        // 布防/撤防
        if (msg.contains("布防") && (msg.contains("撤防") || msg.contains("区别") || msg.contains("是什么"))) {
            return "布防就是让设备进入警戒状态。布防后，传感器检测到异常就会触发告警。\n"
                    + "撤防就是解除警戒状态，传感器正常上报但不产生告警。\n\n"
                    + "一般建议：出门或睡觉时开启布防，在家活动时撤防，避免误报。\n"
                    + "你也可以设置定时布防，让系统在每天固定时间自动切换。";
        }
        if (msg.contains("布防") && msg.contains("怎么")) {
            return "你可以通过语音说「开启布防」来让设备进入警戒状态。\n"
                    + "或者到设备管理页面，找到对应设备，点击布防按钮操作。\n"
                    + "也可以设置定时规则，让设备在特定时间自动布防。";
        }

        // 摄像头离线
        if (msg.contains("摄像头") && (msg.contains("离线") || msg.contains("怎么办"))) {
            return "摄像头离线通常由以下几个原因造成：\n\n"
                    + "1. 设备断电或电源适配器故障 — 检查电源是否正常\n"
                    + "2. 网络连接中断 — 检查 WiFi 信号强度或网线是否松动\n"
                    + "3. MQTT 服务异常 — 后端可能无法接收设备上报\n"
                    + "4. 设备固件死机 — 尝试重启设备\n\n"
                    + "建议先检查电源和网络，如果确认正常但仍离线，"
                    + "可以查看设备最后上报时间，联系管理员处理。";
        }

        // 人体感应
        if (msg.contains("人体感应") || msg.contains("人体检测")) {
            return "人体感应是安防系统通过 PIR 传感器检测人体活动的能力。\n\n"
                    + "当有人经过传感器覆盖区域时，传感器检测到红外变化，"
                    + "上报给系统。如果设备处于布防状态，检测到人体活动就会产生告警。\n\n"
                    + "本系统使用 PIR 被动红外技术，检测范围通常为 5-8 米，"
                    + "角度约 90-110 度。";
        }

        // 告警
        if (msg.contains("告警") && (msg.contains("是什么") || msg.contains("什么意思"))) {
            return "告警是安防系统检测到异常事件后发出的通知。\n\n"
                    + "告警分为三个等级：\n"
                    + "• 高危 — 检测到入侵行为（如人体、陌生人），需要立即关注\n"
                    + "• 中危 — 设备离线、低电量等影响监控效果的事件\n"
                    + "• 低危 — 一般性提醒\n\n"
                    + "告警产生后需要人工确认处理，可以标记为「已处理」或「误报」。";
        }

        // 陌生人
        if (msg.contains("陌生人")) {
            return "陌生人是指人脸识别系统未能匹配到已注册人员的面孔。\n\n"
                    + "当摄像头拍摄到人脸后，系统会与已注册的人脸库进行比对。\n"
                    + "如果匹配失败，就会标记为陌生人并产生告警。\n\n"
                    + "如果你经常有访客，建议提前将他们的面孔注册到系统中，避免误报。";
        }

        // 安防
        if (msg.contains("安防") || msg.contains("安全") && (msg.contains("是什么") || msg.contains("系统"))) {
            return "智慧安防系统由传感器（如 PIR、门磁、摄像头）、"
                    + "后端服务（数据处理、AI 分析）和用户界面三部分组成。\n\n"
                    + "核心功能包括：\n"
                    + "• 设备管理 — 查看和管理所有安防设备\n"
                    + "• 告警管理 — 查看和处理异常事件\n"
                    + "• 实时监测 — 实时查看摄像头画面\n"
                    + "• PIR 监测 — 追踪人体感应记录\n"
                    + "• AI 问答 — 我这个助手可以帮你了解系统状态";
        }

        // MQTT
        if (msg.contains("mqtt")) {
            return "MQTT 是一种轻量级的物联网通信协议。\n\n"
                    + "在本系统中，所有设备通过 MQTT 协议与后端通信，"
                    + "上报传感器数据和接收控制指令。\n"
                    + "MQTT 的优点是低带宽、低功耗，非常适合物联网设备使用。";
        }

        // 求助
        if (msg.contains("求助") && (msg.contains("怎么") || msg.contains("如何"))) {
            return "你可以通过语音说「发送求助」或「帮我通知保安」来快速求助。\n\n"
                    + "系统会自动生成求助信息草稿，包含当前的告警和设备状态，"
                    + "你可以直接发送给紧急联系人。\n\n"
                    + "使用前需要先在消息中心添加紧急联系人。";
        }

        return null; // 无内置匹配
    }

    /**
     * 帮助/功能介绍（仅用于显式的帮助请求）
     */
    private AiAgentResponse handleHelpIntro() {
        AiAgentResponse r = new AiAgentResponse();
        r.setIntent("HELP_INTRO");
        r.setSource("RULE");
        r.setRiskLevel("LOW");
        r.setUsedTools(Collections.emptyList());
        r.setAnswer("你好！我是小安，智慧安防 AI 语音助手。\n\n"
                + "你可以直接问我：\n"
                + "「我现在安全吗？」— 分析当前安防状态\n"
                + "「今天有没有人经过？」— 查询人体感应记录\n"
                + "「当前设备在线吗？」— 查询设备状态\n"
                + "「有没有未处理告警？」— 查询告警信息\n"
                + "「摄像头离线怎么办？」— 安防知识问答\n\n"
                + "你也可以说「打开实时监测」「打开设备管理」来切换页面。\n"
                + "请告诉我你想了解什么？");
        r.setEvidence(Collections.singletonList("用户请求功能介绍"));
        r.setSuggestions(Arrays.asList(
                "试试问「我现在安全吗？」",
                "试试问「当前设备是否在线？」",
                "试试问「最近有没有告警？」",
                "试试问「PIR 是什么原理？」"
        ));
        return r;
    }

    // ==================== PIR 人体感应查询 ====================

    private AiAgentResponse handlePirQuery(String message) {
        AiAgentResponse r = new AiAgentResponse();
        r.setIntent("PIR_QUERY");
        r.setSource("RULE");
        r.setUsedTools(Arrays.asList("pir_service", "human_record"));

        // 尝试注入 PirService（可选依赖）
        PirService pirService = getPirService();

        // 获取 PIR 统计数据
        long todayCount = 0;
        long todayAlarmCount = 0;
        long onlineCount = 0;
        long totalCount = 0;
        long armedCount = 0;
        String latestTime = "暂无记录";
        String latestDevice = "未知";

        if (pirService != null) {
            todayCount = pirService.getTodayDetectionCount();
            Map<String, Object> stats = pirService.getPirStats();
            todayAlarmCount = stats.get("todayAlarmCount") != null ? ((Number) stats.get("todayAlarmCount")).longValue() : 0;
            onlineCount = stats.get("pirDeviceOnline") != null ? ((Number) stats.get("pirDeviceOnline")).longValue() : 0;
            totalCount = stats.get("pirDeviceTotal") != null ? ((Number) stats.get("pirDeviceTotal")).longValue() : 0;
            armedCount = stats.get("pirDeviceArmed") != null ? ((Number) stats.get("pirDeviceArmed")).longValue() : 0;
            Map<String, Object> latest = pirService.getLatestDetection();
            if (latest != null && latest.get("reportTime") != null) {
                latestTime = (String) latest.get("reportTime");
                latestDevice = (String) latest.getOrDefault("deviceCode", "未知");
            }
        } else {
            // 降级：从 HumanRecordMapper 直接查询
            todayCount = 0;
        }

        StringBuilder answer = new StringBuilder();
        answer.append("📡 **PIR 人体感应监测状态**\n\n");

        if (todayCount > 0) {
            answer.append("今日 PIR 设备共检测到 **").append(todayCount).append("** 次人体活动");
            if (todayAlarmCount > 0) {
                answer.append("，其中 **").append(todayAlarmCount).append("** 次发生在布防状态，已生成告警");
            }
            answer.append("。\n\n");
        } else {
            answer.append("今日暂无人体感应记录。\n\n");
        }

        // 设备概览
        answer.append("- 📊 PIR 设备总数：**").append(totalCount).append("** 台\n");
        answer.append("- 🟢 在线设备：**").append(onlineCount).append("** 台\n");
        answer.append("- 🛡️ 已布防设备：**").append(armedCount).append("** 台\n");

        // 最近一次感应
        if (!latestTime.equals("暂无记录")) {
            answer.append("- 🕐 最近一次感应：**").append(latestTime).append("**（设备：").append(latestDevice).append("）\n");
        }

        // 风险评估
        if (todayAlarmCount > 0 && armedCount > 0) {
            r.setRiskLevel("MEDIUM");
            answer.append("\n⚠️ 当前有布防设备检测到人体活动，请关注告警记录。");
            r.setSuggestions(Arrays.asList("查看告警管理处理未处理告警", "确认是否有人在家后再布防", "检查 PIR 设备灵敏度设置"));
        } else if (todayCount > 0 && armedCount == 0) {
            r.setRiskLevel("LOW");
            answer.append("\n✅ 所有设备处于撤防状态，今日人体活动均未触发告警。");
            r.setSuggestions(Arrays.asList("如需安全监控，请开启设备布防", "可在设备管理中设置定时布防"));
        } else {
            r.setRiskLevel("LOW");
            answer.append("\n✅ 当前 PIR 监测状态正常。");
        }

        r.setAnswer(answer.toString());
        return r;
    }

    // ==================== 未知意图 ====================

    private AiAgentResponse handleUnknown(String message) {
        // 检查是否包含安全/安防相关关键词 — 即使未识别意图也不返回欢迎语
        boolean hasSecurityKeyword = containsAny(message,
            "告警", "报警", "警告", "预警", "警报", "异常", "安全",
            "设备", "在线", "离线", "布防", "撤防", "求助", "入侵",
            "陌生人", "PIR", "人体", "感应", "摄像头", "监控",
            "多少", "几条", "今天", "本周", "最近", "未处理");

        if (hasSecurityKeyword) {
            // 尝试用弱匹配再做一次告警查询
            AiAgentResponse r = new AiAgentResponse();
            r.setIntent("ALARM_QUERY");
            r.setSource("RULE");
            r.setRiskLevel("LOW");
            r.setUsedTools(Arrays.asList("alarm_record"));

            List<AlarmRecord> unhandled = alarmRecordService.getUnhandled();
            List<AlarmRecord> allAlarms = alarmRecordService.list(null, null);

            StringBuilder answer = new StringBuilder();
            answer.append("关于「").append(message).append("」：\n\n");

            int total = allAlarms.size();
            int unhandledCount = unhandled.size();
            long highCount = unhandled.stream()
                .filter(a -> "HIGH".equalsIgnoreCase(a.getAlarmLevel())).count();

            if (total == 0) {
                answer.append("当前没有任何告警记录，系统运行正常。");
            } else {
                answer.append("系统共有 ").append(total).append(" 条告警记录");
                if (unhandledCount > 0) {
                    answer.append("，其中 ").append(unhandledCount).append(" 条未处理");
                    if (highCount > 0) answer.append("，").append(highCount).append(" 条为高危");
                }
                answer.append("。");

                // 最近一条
                if (!allAlarms.isEmpty()) {
                    AlarmRecord latest = allAlarms.get(0);
                    answer.append("最近一条告警是").append(latest.getDeviceName())
                          .append("的").append(latest.getAlarmContent()).append("。");
                }

                if (unhandledCount > 0) {
                    answer.append("建议你查看告警详情，及时处理未处理告警。");
                }
            }

            r.setAnswer(answer.toString().trim());
            r.setEvidence(Arrays.asList("弱匹配安全关键词，返回告警概览"));
            r.setSuggestions(Arrays.asList("试试更具体地问「今天有多少告警」",
                                           "问「未处理告警有几条」获取更精确统计"));
            return r;
        }

        // 真正无法识别时才返回引导
        AiAgentResponse r = new AiAgentResponse();
        r.setIntent("UNKNOWN");
        r.setSource("RULE");
        r.setRiskLevel("LOW");
        r.setUsedTools(Collections.emptyList());
        r.setAnswer("你好！我是小安，智慧安防 AI 语音助手。\n\n"
                + "你可以直接问我：\n"
                + "「我现在安全吗？」— 分析当前安防状态\n"
                + "「今天有没有人经过？」— 查询人体感应记录\n"
                + "「当前设备在线吗？」— 查询设备状态\n"
                + "「有没有未处理告警？」— 查询告警信息\n"
                + "「摄像头离线怎么办？」— 安防知识问答\n\n"
                + "你也可以说「打开实时监测」「打开设备管理」来切换页面。\n"
                + "请告诉我你想了解什么？");
        r.setEvidence(Collections.singletonList("无法识别意图，返回通用引导"));
        r.setSuggestions(Arrays.asList(
                "试试问「我现在安全吗？」",
                "试试问「当前设备是否在线？」",
                "试试问「最近有没有告警？」",
                "试试问「摄像头离线怎么办？」"
        ));
        return r;
    }

    // ==================== 工具方法 ====================

    /**
     * 知识库关键词匹配
     */
    private String matchKnowledge(String query) {
        QueryWrapper<Knowledge> wrapper = new QueryWrapper<>();
        wrapper.eq("enabled", 1);
        List<Knowledge> allKnowledge = knowledgeMapper.selectList(wrapper);

        Knowledge bestMatch = null;
        int bestScore = -1;

        for (Knowledge k : allKnowledge) {
            int score = matchScore(query, (k.getKeywords() != null ? k.getKeywords() : "") + " " + k.getQuestion());
            if (score > bestScore) {
                bestScore = score;
                bestMatch = k;
            }
        }

        return (bestMatch != null && bestScore > 0) ? bestMatch.getAnswer() : null;
    }

    private int matchScore(String query, String pattern) {
        int score = 0;
        query = query.toLowerCase();
        pattern = pattern.toLowerCase();

        String[] words = pattern.split("[\\s,，、。；;]+");
        for (String word : words) {
            if (word.length() >= 2 && query.contains(word)) {
                score += 10;
            }
        }

        if (query.contains(pattern)) score += 20;
        if (pattern.contains(query)) score += 15;

        return score;
    }

    /**
     * 生成求助草稿内容
     */
    private String buildHelpDraftContent(List<DeviceStatus> devices,
                                          List<AlarmRecord> unhandled,
                                          List<AlarmRecord> highAlarms,
                                          List<DeviceStatus> offlineDevices) {
        StringBuilder sb = new StringBuilder();
        sb.append("【智慧安防系统自动提醒】\n\n");

        if (!highAlarms.isEmpty()) {
            sb.append("🔴 高危告警（").append(highAlarms.size()).append("条）：\n");
            for (AlarmRecord a : highAlarms) {
                sb.append("• ").append(a.getDeviceName()).append("：")
                  .append(a.getAlarmContent()).append("\n");
            }
            sb.append("\n");
        }

        if (!unhandled.isEmpty()) {
            sb.append("未处理告警共 ").append(unhandled.size()).append(" 条\n\n");
        }

        if (!offlineDevices.isEmpty()) {
            sb.append("离线设备：\n");
            for (DeviceStatus d : offlineDevices) {
                sb.append("• ").append(d.getDeviceName())
                  .append("（").append(d.getLocation()).append("）\n");
            }
            sb.append("\n");
        }

        sb.append("建议操作：\n");
        sb.append("1. 立即查看告警详情，确认现场情况\n");
        sb.append("2. 检查离线设备电源和网络\n");
        sb.append("3. 如确认为紧急情况，请联系相关人员\n");

        sb.append("\n—— 智慧安防系统");
        return sb.toString();
    }

    /**
     * 生成告警消息草稿
     */
    private String buildAlarmDraftContent(List<AlarmRecord> unhandled) {
        StringBuilder sb = new StringBuilder();
        sb.append("【智慧安防告警提醒】\n\n");
        sb.append("当前存在 ").append(unhandled.size()).append(" 条未处理告警：\n\n");

        for (int i = 0; i < Math.min(5, unhandled.size()); i++) {
            AlarmRecord a = unhandled.get(i);
            String time = a.getAlarmTime() != null ? a.getAlarmTime().toString().replace("T", " ") : "未知";
            sb.append(i + 1).append(". ").append(a.getDeviceName())
              .append(" - ").append(a.getAlarmContent())
              .append("\n   等级：").append(a.getAlarmLevelName())
              .append("，时间：").append(time).append("\n");
        }

        sb.append("\n请及时查看并处理告警。");
        sb.append("\n\n—— 智慧安防系统");
        return sb.toString();
    }

    /**
     * 保存聊天记录
     */
    private void saveRecord(Long userId, String message, String answer, String category) {
        try {
            chatRecordMapper.insertRecord(userId, message, answer, null, category);
        } catch (Exception e) {
            System.err.println("[AiAgentService] 聊天记录保存失败: " + e.getMessage());
        }
    }

    /**
     * 空消息响应
     */
    private AiAgentResponse buildEmptyResponse() {
        AiAgentResponse r = new AiAgentResponse();
        r.setIntent("UNKNOWN");
        r.setSource("RULE");
        r.setRiskLevel("LOW");
        r.setAnswer("请输入你的问题。");
        r.setUsedTools(Collections.emptyList());
        r.setEvidence(Collections.emptyList());
        r.setSuggestions(Collections.singletonList("请输入你的安防相关问题"));
        return r;
    }

    // ==================== LLM 调用 (DeepSeek) ====================

    private static final String DEEPSEEK_DEFAULT_URL = "https://api.deepseek.com";
    private static final String DEEPSEEK_DEFAULT_MODEL = "deepseek-v4-pro";
    private static final int LLM_TIMEOUT_MS = 25000;

    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 检查是否配置了 API Key
     */
    private boolean isApiKeyConfigured() {
        String apiKey = System.getenv().getOrDefault("AI_API_KEY", "");
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * 尝试调用 DeepSeek 大模型——失败返回 null，由外层 fallback 到 RULE
     */
    private AiAgentResponse tryLLM(Long userId, String message, String intent,
                                    Map<String, Object> systemContext, String scene) {
        // 知识问答 / 未知意图 → 走纯聊天模式（不强制 JSON）
        if ("KNOWLEDGE_QA".equals(intent) || "UNKNOWN".equals(intent) || "HELP_INTRO".equals(intent)) {
            return tryLLMChat(userId, message, intent, systemContext, scene);
        }
        // 安防状态 / 设备 / 告警 / PIR 查询 → 走结构化 JSON 模式
        return tryLLMStructured(userId, message, intent, systemContext, scene);
    }

    /**
     * 结构化模式 — 强制 JSON 输出，用于安防状态/设备/告警查询
     */
    private AiAgentResponse tryLLMStructured(Long userId, String message, String intent,
                                              Map<String, Object> systemContext, String scene) {
        String apiKey = System.getenv().getOrDefault("AI_API_KEY", "");
        if (apiKey == null || apiKey.trim().isEmpty()) return null;

        String apiUrl = resolveApiUrl();
        String model = resolveModel();

        try {
            String systemPrompt = buildSystemPrompt(scene);
            String userContext = buildUserContext(message, intent, systemContext);

            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model", model);
            reqBody.put("messages", Arrays.asList(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userContext)
            ));
            reqBody.put("stream", false);
            reqBody.put("temperature", 0.2);
            Map<String, String> respFmt = new LinkedHashMap<>();
            respFmt.put("type", "json_object");
            reqBody.put("response_format", respFmt);

            String content = callDeepSeek(apiUrl, apiKey, model, reqBody);
            if (content == null) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> llmResult = objectMapper.readValue(content, Map.class);

            AiAgentResponse result = validateAndConvert(llmResult);
            if (result == null) {
                System.err.println("[AiAgentService] DeepSeek JSON 格式校验失败，fallback RULE");
                return null;
            }

            result.setModel(model);
            result.setFallback(false);
            System.out.println("[AiAgentService] DeepSeek 结构化调用成功, intent=" + result.getIntent()
                    + ", risk=" + result.getRiskLevel() + ", model=" + model);
            return result;

        } catch (Exception e) {
            System.err.println("[AiAgentService] DeepSeek 结构化调用异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 聊天模式 — 自然语言输出，用于知识问答/通用聊天
     */
    private AiAgentResponse tryLLMChat(Long userId, String message, String intent,
                                        Map<String, Object> systemContext, String scene) {
        String apiKey = System.getenv().getOrDefault("AI_API_KEY", "");
        if (apiKey == null || apiKey.trim().isEmpty()) return null;

        String apiUrl = resolveApiUrl();
        String model = resolveModel();

        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是「小安」，智慧安防系统的 AI 语音助手，主要服务视障用户。\n");
            prompt.append("用户通过语音与你交互，你的回答会被文字转语音播报。\n\n");
            prompt.append("要求：\n");
            prompt.append("- 口语化、自然、简洁，每句话不超过 30 个字\n");
            prompt.append("- 先说结论再解释\n");
            prompt.append("- 不要使用 Markdown 格式\n");
            prompt.append("- 如果问的是安防知识，用通俗易懂的方式解释\n");
            prompt.append("- 如果问的是操作类问题（怎么布防/怎么求助），给出具体步骤\n");
            prompt.append("- 不要建议「打开某某页面」或「点击屏幕」\n");
            if (scene != null && !scene.isEmpty()) {
                prompt.append("\n当前场景：").append(scene).append("\n");
            }

            String userMsg = "用户问题：" + message;

            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model", model);
            reqBody.put("messages", Arrays.asList(
                    Map.of("role", "system", "content", prompt.toString()),
                    Map.of("role", "user", "content", userMsg)
            ));
            reqBody.put("stream", false);
            reqBody.put("temperature", 0.5);  // 知识问答可以更有创意
            reqBody.put("max_tokens", 800);

            String content = callDeepSeek(apiUrl, apiKey, model, reqBody);
            if (content == null) return null;

            // 聊天模式：直接使用 LLM 返回的文本
            AiAgentResponse result = new AiAgentResponse();
            result.setAnswer(content.trim());
            result.setIntent(intent);
            result.setRiskLevel("LOW");
            result.setSource("LLM");
            result.setModel(model);
            result.setFallback(false);
            result.setUsedTools(Collections.singletonList("kb_knowledge"));
            result.setEvidence(Collections.singletonList("DeepSeek 知识问答"));

            System.out.println("[AiAgentService] DeepSeek 聊天调用成功, intent=" + intent
                    + ", model=" + model);
            return result;

        } catch (Exception e) {
            System.err.println("[AiAgentService] DeepSeek 聊天调用异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 统一发送 HTTP 请求到 DeepSeek 并返回 content 文本
     */
    private String callDeepSeek(String apiUrl, String apiKey, String model,
                                 Map<String, Object> reqBody) throws Exception {
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

        if (httpResp.statusCode() != 200) {
            System.err.println("[AiAgentService] DeepSeek HTTP " + httpResp.statusCode()
                    + ": " + httpResp.body().substring(0, Math.min(200, httpResp.body().length())));
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> respMap = objectMapper.readValue(httpResp.body(), Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
        if (choices == null || choices.isEmpty()) {
            System.err.println("[AiAgentService] DeepSeek 返回无 choices");
            return null;
        }

        Map<String, Object> firstChoice = choices.get(0);
        Map<String, Object> msg = (Map<String, Object>) firstChoice.get("message");
        if (msg == null) {
            System.err.println("[AiAgentService] DeepSeek 返回无 message");
            return null;
        }

        String content = (String) msg.get("content");
        if (content == null || content.trim().isEmpty()) {
            System.err.println("[AiAgentService] DeepSeek 返回空 content");
            return null;
        }

        // 尝试清理 ```json ``` 包裹
        content = content.trim();
        if (content.startsWith("```")) {
            int start = content.indexOf("\n");
            int end = content.lastIndexOf("```");
            if (start >= 0 && end > start) {
                content = content.substring(start + 1, end).trim();
            }
        }

        return content;
    }

    private String resolveApiUrl() {
        String apiUrl = System.getenv("AI_API_URL");
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            apiUrl = DEEPSEEK_DEFAULT_URL;
        }
        return apiUrl.replaceAll("/+$", "");
    }

    private String resolveModel() {
        String model = System.getenv("AI_MODEL");
        if (model == null || model.trim().isEmpty()) {
            model = DEEPSEEK_DEFAULT_MODEL;
        }
        return model;
    }

    /**
     * 构建 DeepSeek System Prompt
     */
    private String buildSystemPrompt(String scene) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是「小安」，智慧安防系统的 AI 语音助手，主要服务视障用户。\n");
        sb.append("用户通过语音与你交互，你的回答会被文字转语音(TTS)播报出来。\n\n");
        sb.append("核心原则：\n");
        sb.append("1. 回答必须口语化 — 像人和人说话一样，不要用 Markdown、列表符号、表格\n");
        sb.append("2. 简洁优先 — 先说结论，再补充细节。每句话不要太长，适合 TTS 朗读\n");
        sb.append("3. 问答优先 — 用户问什么就答什么，不要建议「打开某某页面」，除非用户明确说「打开」「去」「导航到」\n");
        sb.append("4. 安全分级 — 检测到高危告警时语气要严肃紧急，低风险时轻松自然\n");
        sb.append("5. 视障友好 — 用空间方位描述（如「门口的人体传感器」「客厅的摄像头」）代替纯视觉指示\n\n");
        sb.append("安全限制：\n");
        sb.append("- 你不能自动处理告警、布防、撤防、发送消息\n");
        sb.append("- 所有关键操作必须先说明风险，等待用户确认\n");
        sb.append("- 紧急情况（救命、SOS、着火了）→ 提示拨打 110 或 119\n");

        if (scene != null && !scene.isEmpty()) {
            sb.append("\n当前场景：").append(scene).append("\n");
        }

        sb.append("\n你必须输出严格的 JSON 格式，不要有任何其他内容：\n");
        sb.append("{\n");
        sb.append("  \"answer\": \"口语化的回答（纯文本，不用 Markdown）\",\n");
        sb.append("  \"intent\": \"SECURITY_STATUS / DEVICE_QUERY / ALARM_QUERY / PIR_QUERY / HELP_ADVICE / KNOWLEDGE_QA / UNKNOWN\",\n");
        sb.append("  \"riskLevel\": \"LOW / MEDIUM / HIGH / CRITICAL\",\n");
        sb.append("  \"usedTools\": [\"device_status\", \"alarm_record\", \"kb_knowledge\"],\n");
        sb.append("  \"evidence\": [\"依据1\", \"依据2\"],\n");
        sb.append("  \"suggestions\": [\"口语化的建议1\", \"口语化的建议2\"],\n");
        sb.append("  \"actionDraft\": {\n");
        sb.append("    \"type\": \"HELP_MESSAGE / ALARM_MESSAGE / NONE\",\n");
        sb.append("    \"title\": \"标题\",\n");
        sb.append("    \"content\": \"草稿内容\"\n");
        sb.append("  },\n");
        sb.append("  \"source\": \"LLM\"\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * 构建发送给 DeepSeek 的用户上下文
     * 优先使用前端传来的 systemContext，没有时从数据库构建
     */
    private String buildUserContext(String message, String intent, Map<String, Object> systemContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题：\n").append(message).append("\n\n");

        // 如果有前端传来的实时上下文，优先使用
        if (systemContext != null && !systemContext.isEmpty()) {
            sb.append("以下为前端实时数据：\n");

            // 设备数据
            Object devicesObj = systemContext.get("devices");
            if (devicesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> devices = (List<Map<String, Object>>) devicesObj;
                sb.append("设备状态：\n");
                for (Map<String, Object> d : devices) {
                    String name = String.valueOf(d.getOrDefault("deviceName", d.getOrDefault("name", "未知")));
                    String status = String.valueOf(d.getOrDefault("status", d.getOrDefault("onlineStatus", "UNKNOWN")));
                    sb.append("- ").append(name).append("：").append(status).append("\n");
                }
            } else if (devicesObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> devMap = (Map<String, Object>) devicesObj;
                Object records = devMap.get("records");
                if (records instanceof List) {
                    sb.append("设备状态：\n");
                    for (Object r : (List<?>) records) {
                        if (r instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> d = (Map<String, Object>) r;
                            String name = String.valueOf(d.getOrDefault("deviceName", d.getOrDefault("name", "未知")));
                            String status = String.valueOf(d.getOrDefault("status", "UNKNOWN"));
                            sb.append("- ").append(name).append("：").append(status).append("\n");
                        }
                    }
                }
            }

            // 告警数据
            Object alarmsObj = systemContext.get("alarms");
            if (alarmsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> alarms = (List<Map<String, Object>>) alarmsObj;
                sb.append("\n未处理告警：\n");
                if (alarms.isEmpty()) {
                    sb.append("（无未处理告警）\n");
                } else {
                    for (Map<String, Object> a : alarms) {
                        String title = String.valueOf(a.getOrDefault("title", a.getOrDefault("alarmContent", "未知")));
                        String level = String.valueOf(a.getOrDefault("level", a.getOrDefault("alarmLevel", "MEDIUM")));
                        String handleStatus = String.valueOf(a.getOrDefault("handleStatus", "UNHANDLED"));
                        sb.append("- ").append(level).append("：").append(title)
                          .append("（处理状态：").append(handleStatus).append("）\n");
                    }
                }
            }

            // PIR 统计
            Object pirObj = systemContext.get("pirStats");
            if (pirObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> pir = (Map<String, Object>) pirObj;
                sb.append("\nPIR 人体感应统计：\n");
                sb.append("- 今日检测次数：").append(pir.getOrDefault("todayCount", 0)).append("\n");
            }

            // 人脸状态
            Object faceObj = systemContext.get("faceStatus");
            if (faceObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> face = (Map<String, Object>) faceObj;
                sb.append("\n人脸识别状态：\n");
                sb.append("- 在线：").append(face.getOrDefault("online", false)).append("\n");
            }
        } else {
            // 没有前端上下文，从数据库构建
            // 设备状态
            List<DeviceStatus> devices = deviceStatusService.getAll();
            sb.append("设备状态：\n");
            if (devices.isEmpty()) {
                sb.append("（无设备数据）\n");
            } else {
                for (DeviceStatus d : devices) {
                    String onlineText = (d.getOnline() != null && d.getOnline() == 1) ? "在线" : "离线";
                    String defenseText = (d.getDefense() != null && d.getDefense() == 1) ? "已布防" : "已撤防";
                    String batteryText = d.getBattery() != null ? d.getBattery() + "%" : "N/A";
                    sb.append("- ").append(d.getDeviceName())
                      .append("：").append(onlineText)
                      .append("，").append(defenseText)
                      .append("，电量 ").append(batteryText)
                      .append("，位置 ").append(d.getLocation() != null ? d.getLocation() : "未知")
                      .append("\n");
                }
            }

            // 告警
            List<AlarmRecord> unhandled = alarmRecordService.getUnhandled();
            sb.append("\n未处理告警：\n");
            if (unhandled.isEmpty()) {
                sb.append("（无未处理告警）\n");
            } else {
                for (AlarmRecord a : unhandled) {
                    String time = a.getAlarmTime() != null ? a.getAlarmTime().toString().replace("T", " ") : "未知";
                    sb.append("- ").append(a.getAlarmLevelName()).append("：")
                      .append(a.getDeviceName()).append("，")
                      .append(a.getAlarmContent())
                      .append("（").append(time).append("）\n");
                }
            }

            // 离线/低电量设备摘要
            long offlineCount = devices.stream().filter(d -> d.getOnline() != null && d.getOnline() == 0).count();
            long lowBatteryCount = devices.stream().filter(d -> d.getBattery() != null && d.getBattery() < 20).count();
            if (offlineCount > 0 || lowBatteryCount > 0) {
                sb.append("\n异常设备摘要：\n");
                if (offlineCount > 0) sb.append("- 离线设备：").append(offlineCount).append(" 台\n");
                if (lowBatteryCount > 0) sb.append("- 低电量设备：").append(lowBatteryCount).append(" 台\n");
            }
        }

        // 知识库摘要
        String kbMatch = matchKnowledge(message);
        sb.append("\n知识库匹配结果：\n");
        sb.append(kbMatch != null ? kbMatch : "（无匹配知识库条目）");

        // 用户场景
        sb.append("\n\n用户场景：\n");
        sb.append("- 用户是视障人士，通过语音与你交互，你的回答会被 TTS 播报\n");
        sb.append("- 不要建议用户「点击」「查看屏幕」，用「可以说」「我帮你查」代替\n");
        sb.append("- 回答要像对话一样自然，适合朗读，每句话不超过 30 个字\n");
        sb.append("- 不说「打开某某页面」除非用户明确说「打开」「去」「导航到」\n");
        sb.append("- 听到「救命」「SOS」「着火了」「快报警」→ 立即建议拨打 110/119\n");

        // 安全限制
        sb.append("\n安全限制：\n");
        sb.append("- 你不能自动处理告警，只能建议\n");
        sb.append("- 你不能自动发送消息，只能生成草稿\n");
        sb.append("- 你必须标记 source 为 LLM\n");
        sb.append("- actionDraft.type 为 NONE 时可以省略 actionDraft\n");

        // 提示
        sb.append("\n请只返回 JSON，不要输出任何其他内容。");

        return sb.toString();
    }

    /**
     * 校验 LLM 返回的 JSON 并转换为 AiAgentResponse
     */
    private AiAgentResponse validateAndConvert(Map<String, Object> raw) {
        if (raw == null) return null;

        try {
            AiAgentResponse r = new AiAgentResponse();

            // answer — 必填
            Object answerObj = raw.get("answer");
            if (answerObj == null) return null;
            r.setAnswer(String.valueOf(answerObj));

            // intent — 必填，限定枚举
            Object intentObj = raw.get("intent");
            String intent = intentObj != null ? String.valueOf(intentObj) : "UNKNOWN";
            if (!Arrays.asList("SECURITY_STATUS", "DEVICE_QUERY", "ALARM_QUERY",
                    "HELP_ADVICE", "KNOWLEDGE_QA", "UNKNOWN").contains(intent)) {
                intent = "UNKNOWN";
            }
            r.setIntent(intent);

            // riskLevel — 必填，限定枚举
            Object riskObj = raw.get("riskLevel");
            String riskLevel = riskObj != null ? String.valueOf(riskObj) : "LOW";
            if (!Arrays.asList("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(riskLevel)) {
                riskLevel = "LOW";
            }
            r.setRiskLevel(riskLevel);

            // usedTools — 必须是数组
            Object toolsObj = raw.get("usedTools");
            List<String> tools = new ArrayList<>();
            if (toolsObj instanceof List) {
                for (Object t : (List<?>) toolsObj) {
                    tools.add(String.valueOf(t));
                }
            }
            r.setUsedTools(tools.isEmpty() ? Collections.singletonList("kb_knowledge") : tools);

            // evidence — 必须是数组
            Object evObj = raw.get("evidence");
            List<String> evidence = new ArrayList<>();
            if (evObj instanceof List) {
                for (Object e : (List<?>) evObj) {
                    evidence.add(String.valueOf(e));
                }
            }
            r.setEvidence(evidence);

            // suggestions — 必须是数组
            Object sugObj = raw.get("suggestions");
            List<String> suggestions = new ArrayList<>();
            if (sugObj instanceof List) {
                for (Object s : (List<?>) sugObj) {
                    suggestions.add(String.valueOf(s));
                }
            }
            r.setSuggestions(suggestions);

            // actionDraft — 可选
            Object adObj = raw.get("actionDraft");
            if (adObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> adMap = (Map<String, Object>) adObj;
                String adType = adMap.get("type") != null ? String.valueOf(adMap.get("type")) : "NONE";
                if (!"NONE".equals(adType)) {
                    ActionDraft draft = new ActionDraft();
                    draft.setType(adType);
                    draft.setTitle(adMap.get("title") != null ? String.valueOf(adMap.get("title")) : "");
                    draft.setContent(adMap.get("content") != null ? String.valueOf(adMap.get("content")) : "");
                    r.setActionDraft(draft);
                }
            }

            // source — 强制 LLM
            r.setSource("LLM");

            return r;
        } catch (Exception e) {
            System.err.println("[AiAgentService] validateAndConvert 异常: " + e.getMessage());
            return null;
        }
    }
}
