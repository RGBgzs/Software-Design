package com.example.security.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.security.entity.AlarmRecord;
import com.example.security.entity.DeviceStatus;
import com.example.security.entity.Knowledge;
import com.example.security.mapper.ChatRecordMapper;
import com.example.security.mapper.KnowledgeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class KnowledgeChatService {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private ChatRecordMapper chatRecordMapper;

    @Autowired
    private DeviceStatusService deviceStatusService;

    @Autowired
    private AlarmRecordService alarmRecordService;

    // ==================== 告警检测 ====================

    /** 告警强模式：命中任意一个 → 直接走告警查询 */
    private static final String[] STRONG_ALARM_PATTERNS = {
        "最近有没有告警", "今天有异常吗", "未处理告警", "查看最新告警",
        "有没有入侵", "当前有告警吗", "有人闯入了吗", "是否有陌生人",
        "最近告警", "最新告警", "有告警吗", "查看告警", "有没有告警",
        "有没有异常", "当前入侵", "是否入侵", "入侵告警"
    };

    /** 告警弱关键词：命中 ≥2 个 → 走告警查询 */
    private static final String[] ALARM_KEYWORDS = {
        "告警", "异常", "入侵", "闯入", "陌生人", "未处理", "危险", "报警"
    };

    // ==================== 设备状态检测 ====================

    /** 强设备状态模式：命中任意一个即判定为设备状态查询 */
    private static final String[] STRONG_DEVICE_PATTERNS = {
        "是否在线", "在线吗", "是否布防", "布防吗", "是否离线", "离线吗",
        "电量多少", "电量还有", "查看设备状态", "查看设备", "当前设备",
        "设备是否", "设备状态", "当前状态"
    };

    /** 设备状态关键词（弱匹配：命中 ≥2 个才触发） */
    private static final String[] DEVICE_KEYWORDS = {
        "设备", "在线", "离线", "状态", "布防", "撤防", "电量", "人体传感器", "摄像头"
    };

    /**
     * 处理用户消息
     */
    public Map<String, Object> chat(Long userId, String message) {
        Map<String, Object> result = new HashMap<>();

        if (message == null || message.trim().isEmpty()) {
            result.put("answer", "请输入你的问题。");
            result.put("matchedKnowledgeId", null);
            result.put("category", null);
            return result;
        }

        // ---- 优先顺序 ----
        // 1. 告警强模式
        if (isAlarmStrongQuery(message)) {
            return handleAlarmQuery(userId, message);
        }

        // 2. 设备状态强模式
        if (isDeviceStatusQuery(message)) {
            return handleDeviceStatusQuery(userId, message);
        }

        // 3. 告警弱关键词 >= 2
        if (countAlarmKeywords(message) >= 2) {
            return handleAlarmQuery(userId, message);
        }

        // 4. 设备状态弱关键词 >= 2
        if (countDeviceKeywords(message) >= 2) {
            return handleDeviceStatusQuery(userId, message);
        }

        // 5. 知识库匹配
        return handleKnowledgeQuery(userId, message);
    }

    // ==================== 告警查询 ====================

    private boolean isAlarmStrongQuery(String message) {
        for (String pattern : STRONG_ALARM_PATTERNS) {
            if (message.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private int countAlarmKeywords(String message) {
        int hits = 0;
        for (String kw : ALARM_KEYWORDS) {
            if (message.contains(kw)) hits++;
        }
        return hits;
    }

    private Map<String, Object> handleAlarmQuery(Long userId, String message) {
        Map<String, Object> result = new HashMap<>();

        List<AlarmRecord> unhandled = alarmRecordService.getUnhandled();
        String answer;

        if (unhandled.isEmpty()) {
            answer = "当前暂无未处理告警，系统处于正常监测状态。";
        } else {
            answer = alarmRecordService.buildAlarmSummary(unhandled);
        }

        saveRecord(userId, message, answer, null, "告警查询");

        result.put("answer", answer);
        result.put("matchedKnowledgeId", null);
        result.put("category", "告警查询");
        return result;
    }

    // ==================== 设备状态查询 ====================

    private boolean isDeviceStatusQuery(String message) {
        for (String pattern : STRONG_DEVICE_PATTERNS) {
            if (message.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private int countDeviceKeywords(String message) {
        int hits = 0;
        for (String kw : DEVICE_KEYWORDS) {
            if (message.contains(kw)) hits++;
        }
        return hits;
    }

    private Map<String, Object> handleDeviceStatusQuery(Long userId, String message) {
        Map<String, Object> result = new HashMap<>();

        List<DeviceStatus> devices = deviceStatusService.getAll();
        String answer = deviceStatusService.buildStatusSummary(devices);

        saveRecord(userId, message, answer, null, "设备状态");

        result.put("answer", answer);
        result.put("matchedKnowledgeId", null);
        result.put("category", "设备状态");
        return result;
    }

    // ==================== 知识库查询 ====================

    private Map<String, Object> handleKnowledgeQuery(Long userId, String message) {
        Map<String, Object> result = new HashMap<>();

        QueryWrapper<Knowledge> wrapper = new QueryWrapper<>();
        wrapper.eq("enabled", 1);
        List<Knowledge> allKnowledge = knowledgeMapper.selectList(wrapper);

        Knowledge bestMatch = null;
        int bestScore = -1;

        for (Knowledge k : allKnowledge) {
            int score = matchScore(message, k.getKeywords() + " " + k.getQuestion());
            if (score > bestScore) {
                bestScore = score;
                bestMatch = k;
            }
        }

        String answer;
        Long matchedId = null;
        String category = "未匹配";

        if (bestMatch != null && bestScore > 0) {
            answer = bestMatch.getAnswer();
            matchedId = bestMatch.getId();
            category = bestMatch.getCategory();

            // "发现陌生人怎么办？"这类问题走知识库，但如果数据库有未处理陌生人告警，则在答案前附加当前告警状态
            if (message.contains("陌生人") || message.contains("入侵") || message.contains("闯入")) {
                List<AlarmRecord> unhandled = alarmRecordService.getUnhandled();
                if (!unhandled.isEmpty()) {
                    String prefix = "⚠️ 当前存在未处理告警，请注意：\n\n";
                    for (AlarmRecord a : unhandled) {
                        String time = (a.getAlarmTime() != null)
                                ? a.getAlarmTime().toString().replace("T", " ")
                                : "未知";
                        prefix += "• " + a.getDeviceName() + "：" + a.getAlarmContent()
                                + "（" + a.getAlarmLevelName() + "，" + time + "）\n";
                    }
                    prefix += "\n以下是知识库建议：\n\n";
                    answer = prefix + answer;
                }
            }
        } else {
            answer = "抱歉，知识库中暂时没有找到关于「" + message + "」的相关信息。\n\n你可以尝试：\n• 系统如何布防？\n• 发现陌生人怎么办？\n• 短信通知什么时候发送？\n• 设备离线怎么办？\n• 人体传感器有什么作用？\n• 当前设备是否在线？\n• 最近有没有告警？";
        }

        saveRecord(userId, message, answer, matchedId, category);

        result.put("answer", answer);
        result.put("matchedKnowledgeId", matchedId);
        result.put("category", category);
        return result;
    }

    // ==================== 工具方法 ====================

    /**
     * 保存聊天记录
     */
    private void saveRecord(Long userId, String message, String answer, Long matchedId, String category) {
        try {
            chatRecordMapper.insertRecord(userId, message, answer, matchedId, category);
        } catch (Exception e) {
            System.err.println("[KnowledgeChatService] 聊天记录保存失败: " + e.getMessage());
        }
    }

    /**
     * 关键词匹配评分
     */
    private int matchScore(String query, String pattern) {
        int score = 0;
        query = query.toLowerCase();
        pattern = pattern.toLowerCase();

        String[] patternWords = pattern.split("[\\s,，、。；;]+");
        for (String word : patternWords) {
            if (word.length() >= 2 && query.contains(word)) {
                score += 10;
            }
        }

        if (query.contains(pattern)) {
            score += 20;
        }
        if (pattern.contains(query)) {
            score += 15;
        }

        return score;
    }
}
