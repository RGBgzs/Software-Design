package com.example.security.controller;

import com.example.security.service.KnowledgeChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/kb")
public class KbChatController {

    @Autowired
    private KnowledgeChatService knowledgeChatService;

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null
                ? ((Number) body.get("userId")).longValue()
                : 1L;
        String message = (String) body.get("message");

        return knowledgeChatService.chat(userId, message);
    }
}
