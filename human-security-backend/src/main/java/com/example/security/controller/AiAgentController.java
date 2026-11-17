package com.example.security.controller;

import com.example.security.common.ApiResponse;
import com.example.security.dto.ActionDraft;
import com.example.security.dto.AiAgentRequest;
import com.example.security.dto.AiAgentResponse;
import com.example.security.dto.VoiceCommandRequest;
import com.example.security.dto.VoiceCommandResponse;
import com.example.security.service.AiAgentService;
import com.example.security.service.VoiceCommandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collections;

@RestController
@RequestMapping("/api/ai")
public class AiAgentController {

    @Autowired
    private AiAgentService aiAgentService;

    @Autowired
    private VoiceCommandService voiceCommandService;

    /**
     * POST /api/ai/agent-chat
     * AI 安防智能体助手接口
     */
    @PostMapping("/agent-chat")
    public ApiResponse<AiAgentResponse> agentChat(@RequestBody AiAgentRequest request) {
        try {
            AiAgentResponse result = aiAgentService.chat(
                    request.getUserId() != null ? request.getUserId() : 1L,
                    request.getMessage(),
                    request.getSystemContext(),
                    request.getScene()
            );
            return ApiResponse.success(result);
        } catch (Exception e) {
            // 异常兜底：返回安全的 fallback 结果
            AiAgentResponse fallback = new AiAgentResponse();
            fallback.setAnswer("抱歉，AI 智能体暂时无法处理你的问题。请稍后重试或联系管理员。");
            fallback.setIntent("UNKNOWN");
            fallback.setRiskLevel("LOW");
            fallback.setSource("FALLBACK");
            fallback.setFallback(true);
            fallback.setErrorType("SERVICE_EXCEPTION");
            fallback.setUsedTools(Collections.emptyList());
            fallback.setEvidence(Collections.singletonList("服务异常：" + e.getMessage()));
            fallback.setSuggestions(Arrays.asList(
                    "请稍后重试",
                    "检查后端服务状态",
                    "尝试使用快捷问题"
            ));
            return ApiResponse.success(fallback);
        }
    }

    /**
     * POST /api/ai/voice-command
     * 语音命令识别接口 — AI 语音助手
     */
    @PostMapping("/voice-command")
    public ApiResponse<VoiceCommandResponse> voiceCommand(@RequestBody VoiceCommandRequest request) {
        try {
            VoiceCommandResponse result = voiceCommandService.process(
                    request.getText() != null ? request.getText() : ""
            );
            return ApiResponse.success(result);
        } catch (Exception e) {
            VoiceCommandResponse fallback = new VoiceCommandResponse();
            fallback.setAnswer("抱歉，语音命令处理暂时不可用，请稍后重试。");
            fallback.setIntent("UNKNOWN");
            fallback.setActionType("NONE");
            fallback.setNeedConfirm(false);
            fallback.setRiskLevel("LOW");
            return ApiResponse.success(fallback);
        }
    }
}
