package com.example.security.controller;

import com.example.security.dto.AiJudgeRequest;
import com.example.security.dto.AiJudgeResult;
import com.example.security.service.AiJudgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiJudgeController {

    @Autowired
    private AiJudgeService aiJudgeService;

    /**
     * POST /api/ai/judge
     * Run AI risk assessment
     */
    @PostMapping("/judge")
    public Map<String, Object> judge(@RequestBody AiJudgeRequest request) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long alarmId = request != null ? request.getAlarmId() : null;
            AiJudgeResult judgeResult = aiJudgeService.judge(alarmId);

            result.put("code", 200);
            result.put("message", "success");
            result.put("data", judgeResult);
        } catch (Exception e) {
            // FALLBACK: return a safe result if something goes wrong
            AiJudgeResult fallback = new AiJudgeResult();
            fallback.setRiskLevel("MEDIUM");
            fallback.setEventType("UNKNOWN");
            fallback.setSummary("当前数据不足，无法完成准确判断");
            fallback.setReason("系统分析数据时出现异常：" + e.getMessage());
            fallback.setSuggestions(java.util.Arrays.asList(
                "检查数据库数据是否正常",
                "检查设备是否正常上报",
                "检查后端服务状态"
            ));
            fallback.setNeedHumanConfirm(true);
            fallback.setConfidence(0.3);
            fallback.setSource("FALLBACK");
            fallback.setFallback(true);
            fallback.setErrorType("SERVICE_EXCEPTION");

            result.put("code", 200);
            result.put("message", "fallback");
            result.put("data", fallback);
        }

        return result;
    }
}
