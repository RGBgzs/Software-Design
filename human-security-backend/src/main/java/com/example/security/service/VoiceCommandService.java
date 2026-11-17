package com.example.security.service;

import com.example.security.dto.VoiceCommandResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 语音命令识别服务（规则匹配）
 * 前端语音助手的后端支持，识别语音命令并返回执行指令
 */
@Service
public class VoiceCommandService {

    @Autowired
    private AiAgentService aiAgentService;

    @Autowired(required = false)
    private PirService pirService;

    // ==================== 危险操作关键词 ====================
    private static final Set<String> DANGER_KEYWORDS = new HashSet<>(Arrays.asList(
            "发送求助", "帮我报警", "报警", "删除这个", "删除人员", "删除告警",
            "处理这条告警", "处理告警", "撤防", "发送消息", "帮我通知",
            "开启布防", "撤销布防"
    ));

    private static final Set<String> CONFIRM_KEYWORDS = new HashSet<>(Arrays.asList(
            "确认", "确认执行", "确定", "是的", "执行", "好的"
    ));

    // ==================== 主入口 ====================

    public VoiceCommandResponse process(String text) {
        if (text == null || text.trim().isEmpty()) {
            return buildEmptyResponse();
        }

        String msg = text.trim();

        // ── 1. 确认关键词（确认执行之前的危险操作）──
        if (isConfirm(msg)) {
            return buildConfirmResponse();
        }

        // ── 2. 危险操作 ──
        if (isDangerCommand(msg)) {
            return buildDangerResponse(msg);
        }

        // ── 3. 打开首页 ──
        if (containsAny(msg, "首页", "总控台", "主页", "首页概览")) {
            return buildActionResponse("OPEN_HOME", "打开首页", "OPEN_SECTION", "home",
                    "已为你打开首页总控台。");
        }

        // ── 4. 停止实时检测（必须在启动之前判断）──
        if (containsAny(msg, "停止检测", "关闭摄像头", "停止实时", "关闭实时检测",
                "结束检测", "停止人脸检测")) {
            return buildActionResponse("STOP_FACE_LIVE", "停止实时检测", "STOP_FACE_LIVE", null,
                    "已为你停止实时检测。");
        }

        // ── 5. 打开实时检测 / 启动实时检测 ──
        if (containsAny(msg, "实时检测", "打开摄像头", "人脸实时检测", "启动实时检测",
                "开启实时检测", "开始检测", "开始实时", "打开实时")) {
            return buildActionResponse("START_FACE_LIVE", "启动实时检测", "START_FACE_LIVE", "face-live",
                    "已为你打开实时检测，请确认摄像头权限。");
        }

        // ── 6. 打开人脸识别 ──
        if (containsAny(msg, "人脸识别", "人脸记录", "识别记录")) {
            return buildActionResponse("OPEN_FACE", "打开人脸识别", "OPEN_SECTION", "face",
                    "已为你打开人脸识别记录。");
        }

        // ── 7. 打开人员管理 ──
        if (containsAny(msg, "人员管理", "注册人员", "人脸注册", "注册人脸")) {
            return buildActionResponse("OPEN_FACE_USERS", "打开人员管理", "OPEN_SECTION", "face-users",
                    "已为你打开人员管理页面。");
        }

        // ── 8. 打开语音播报 ──
        if (containsAny(msg, "语音播报", "播报", "文字转语音", "语音合成", "TTS")) {
            return buildActionResponse("OPEN_FACE_TTS", "打开语音播报", "OPEN_SECTION", "face-tts",
                    "已为你打开语音播报页面。");
        }

        // ── 9. 打开告警管理 ──
        if (containsAny(msg, "告警", "报警", "高危告警", "陌生人告警")) {
            return buildActionResponse("OPEN_ALARM", "打开告警管理", "OPEN_SECTION", "alarm",
                    "已为你打开告警管理页面。");
        }

        // ── 10. 打开设备管理 ──
        if (containsAny(msg, "设备", "设备管理", "摄像头状态", "传感器")) {
            return buildActionResponse("OPEN_DEVICE", "打开设备管理", "OPEN_SECTION", "device",
                    "已为你打开设备管理页面。");
        }

        // ── 11. PIR 人体感应监测 ──
        if (containsAny(msg, "PIR", "人体感应", "人体检测", "人体监测", "感应状态",
                "感应记录", "查看人体", "打开人体", "人体传感器", "红外")) {
            return buildActionResponse("OPEN_PIR", "打开PIR监测", "OPEN_SECTION", "pir",
                    "已为你打开 PIR 人体感应监测页面。");
        }

        // ── 12. PIR 查询 - 今天有没有人经过 ──
        if (containsAny(msg, "今天有没有人", "有人经过", "有人来过",
                "今天有人", "今天检测", "今天感应")) {
            return queryPirToday(msg);
        }

        // ── 13. 打开消息中心 ──
        if (containsAny(msg, "消息", "消息中心", "站内消息", "收件箱", "发件箱")) {
            return buildActionResponse("OPEN_MESSAGE", "打开消息中心", "OPEN_SECTION", "message",
                    "已为你打开消息中心。");
        }

        // ── 14. 打开紧急求助 ──
        if (containsAny(msg, "求助", "紧急求助", "联系家人", "生成求助")) {
            return buildActionResponse("OPEN_HELP", "打开紧急求助", "OPEN_SECTION", "help",
                    "已为你打开紧急求助页面。");
        }

        // ── 15. 查询安防状态 ──
        if (containsAny(msg, "安全吗", "当前状态", "家里安全", "有没有危险", "是否安全",
                "现在安全", "安防状态", "安全状态", "安全情况")) {
            return querySecurityStatus(msg);
        }

        // ── 16. 查询陌生人 ──
        if (containsAny(msg, "陌生人")) {
            return buildActionResponse("OPEN_FACE_RECORD", "查询陌生人记录", "OPEN_SECTION", "face",
                    "已为你打开人脸识别记录，请查看陌生人识别结果。");
        }

        // ── 17. 最新告警 ──
        if (containsAny(msg, "最新告警", "最近告警", "查看告警")) {
            return buildActionResponse("OPEN_ALARM", "查询最新告警", "OPEN_SECTION", "alarm",
                    "已为你打开告警管理，请查看最新告警记录。");
        }

        // ── 18. 默认：调用 AI 智能体 ──
        return queryAiAgent(msg);
    }

    // ==================== 关键词匹配 ====================

    private boolean containsAny(String msg, String... keywords) {
        for (String kw : keywords) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }

    private boolean isDangerCommand(String msg) {
        for (String kw : DANGER_KEYWORDS) {
            if (msg.contains(kw)) return true;
        }
        return false;
    }

    private boolean isConfirm(String msg) {
        for (String kw : CONFIRM_KEYWORDS) {
            if (msg.equals(kw)) return true;
        }
        return false;
    }

    // ==================== 响应构建 ====================

    private VoiceCommandResponse buildActionResponse(String intent, String actionType,
                                                      String target, String answer) {
        VoiceCommandResponse r = new VoiceCommandResponse();
        r.setIntent(intent);
        r.setActionType(actionType);
        r.setTarget(target);
        r.setAnswer(answer);
        r.setNeedConfirm(false);
        r.setRiskLevel("LOW");
        r.setActionData(new HashMap<>());
        return r;
    }

    private VoiceCommandResponse buildActionResponse(String intent, String intentLabel,
                                                      String actionType, String target, String answer) {
        return buildActionResponse(intent, actionType, target, answer);
    }

    private VoiceCommandResponse buildDangerResponse(String msg) {
        VoiceCommandResponse r = new VoiceCommandResponse();
        r.setIntent("DANGER_CONFIRM");
        r.setActionType("NONE");
        r.setTarget(null);
        r.setAnswer("该操作较敏感，请说「确认执行」后继续。");
        r.setNeedConfirm(true);
        r.setRiskLevel("HIGH");
        Map<String, Object> data = new HashMap<>();
        data.put("originalCommand", msg);
        r.setActionData(data);
        return r;
    }

    private VoiceCommandResponse buildConfirmResponse() {
        VoiceCommandResponse r = new VoiceCommandResponse();
        r.setIntent("CONFIRM_OK");
        r.setActionType("NONE");
        r.setTarget(null);
        r.setAnswer("已确认，请重新说出你的指令。");
        r.setNeedConfirm(false);
        r.setRiskLevel("LOW");
        r.setActionData(new HashMap<>());
        return r;
    }

    private VoiceCommandResponse buildEmptyResponse() {
        VoiceCommandResponse r = new VoiceCommandResponse();
        r.setIntent("UNKNOWN");
        r.setActionType("NONE");
        r.setTarget(null);
        r.setAnswer("我在，请说出你的指令。");
        r.setNeedConfirm(false);
        r.setRiskLevel("LOW");
        r.setActionData(new HashMap<>());
        return r;
    }

    // ==================== PIR 今日查询 ====================

    private VoiceCommandResponse queryPirToday(String msg) {
        if (pirService != null) {
            long todayCount = pirService.getTodayDetectionCount();
            Map<String, Object> stats = pirService.getPirStats();
            long todayAlarmCount = stats.get("todayAlarmCount") != null
                    ? ((Number) stats.get("todayAlarmCount")).longValue() : 0;

            Map<String, Object> latest = pirService.getLatestDetection();
            String latestTime = latest.get("reportTime") != null
                    ? (String) latest.get("reportTime") : "暂无";

            StringBuilder answer = new StringBuilder();
            answer.append("今天 PIR 设备共检测到 ").append(todayCount).append(" 次人体活动");
            if (todayAlarmCount > 0) {
                answer.append("，其中 ").append(todayAlarmCount).append(" 次发生在布防状态，已生成告警");
            }
            answer.append("。");
            if (!"暂无".equals(latestTime)) {
                answer.append("最近一次感应时间：").append(latestTime).append("。");
            }

            VoiceCommandResponse r = new VoiceCommandResponse();
            r.setIntent("PIR_QUERY");
            r.setActionType("NONE");
            r.setTarget(null);
            r.setAnswer(answer.toString());
            r.setNeedConfirm(false);
            r.setRiskLevel(todayAlarmCount > 0 ? "MEDIUM" : "LOW");
            r.setActionData(new HashMap<>());
            return r;
        }

        // 降级：调用 AI 智能体
        return queryAiAgent(msg);
    }

    // ==================== AI 智能体兜底 ====================

    private VoiceCommandResponse querySecurityStatus(String msg) {
        try {
            var aiResp = aiAgentService.chat(1L, msg);
            VoiceCommandResponse r = new VoiceCommandResponse();
            r.setIntent("SECURITY_STATUS");
            r.setActionType("NONE");
            r.setTarget(null);
            r.setAnswer(aiResp.getAnswer());
            r.setNeedConfirm(false);
            r.setRiskLevel(aiResp.getRiskLevel() != null ? aiResp.getRiskLevel() : "LOW");
            r.setActionData(new HashMap<>());
            return r;
        } catch (Exception e) {
            VoiceCommandResponse r = new VoiceCommandResponse();
            r.setIntent("SECURITY_STATUS");
            r.setActionType("NONE");
            r.setTarget(null);
            r.setAnswer("抱歉，暂时无法查询安防状态，请稍后重试。");
            r.setNeedConfirm(false);
            r.setRiskLevel("LOW");
            r.setActionData(new HashMap<>());
            return r;
        }
    }

    private VoiceCommandResponse queryAiAgent(String msg) {
        try {
            var aiResp = aiAgentService.chat(1L, msg);
            VoiceCommandResponse r = new VoiceCommandResponse();
            r.setIntent(aiResp.getIntent() != null ? aiResp.getIntent() : "UNKNOWN");
            r.setActionType("NONE");
            r.setTarget(null);
            r.setAnswer(aiResp.getAnswer());
            r.setNeedConfirm(false);
            r.setRiskLevel(aiResp.getRiskLevel() != null ? aiResp.getRiskLevel() : "LOW");
            r.setActionData(new HashMap<>());
            return r;
        } catch (Exception e) {
            VoiceCommandResponse r = new VoiceCommandResponse();
            r.setIntent("UNKNOWN");
            r.setActionType("NONE");
            r.setTarget(null);
            r.setAnswer("抱歉，我暂时无法处理你的问题。请稍后重试或换个说法。");
            r.setNeedConfirm(false);
            r.setRiskLevel("LOW");
            r.setActionData(new HashMap<>());
            return r;
        }
    }
}
