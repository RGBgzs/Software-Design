package com.example.security.controller;

import com.example.security.entity.ChatRecord;
import com.example.security.service.ChatRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/chat-record")
public class ChatRecordController {

    @Autowired
    private ChatRecordService chatRecordService;

    /**
     * GET /api/chat-record/list
     * 可选参数：keyword
     */
    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam(required = false) String keyword) {
        List<ChatRecord> list = chatRecordService.list(keyword);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("total", list.size());
        result.put("data", list);
        return result;
    }
}
