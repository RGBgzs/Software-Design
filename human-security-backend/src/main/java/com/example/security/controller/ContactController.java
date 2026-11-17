package com.example.security.controller;

import com.example.security.common.Result;
import com.example.security.entity.UserMessage;
import com.example.security.service.ContactService;
import com.example.security.service.UserMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 联系人 + 消息 Controller
 *
 * 实际路径：
 *   POST   /api/contacts/add       添加联系人
 *   GET    /api/contacts/list      联系人列表
 *   DELETE /api/contacts/delete    删除联系人
 *   POST   /api/messages/help      紧急求助
 *   POST   /api/messages/send      发送消息
 *   GET    /api/messages/conversation   聊天记录
 *   GET    /api/messages/conversations  会话列表
 */
@RestController
public class ContactController {

    @Autowired private ContactService contactService;
    @Autowired private UserMessageService msgService;

    // ═══════════════════════════════════════════
    //  紧急联系人 — /api/contacts/*
    // ═══════════════════════════════════════════

    @PostMapping("/api/contacts/add")
    public Result<?> addContact(@RequestBody Map<String, Object> body) {
        Long userId = getLong(body, "userId", "currentUserId");
        if (userId == null) userId = 1L;

        String contactUsername = getString(body, "contactUsername", "username", "targetUsername");
        if (contactUsername == null || contactUsername.isBlank()) {
            return Result.fail(400, "联系人用户名不能为空");
        }

        String phone = getString(body, "phone");
        String relation = getString(body, "relation");

        System.out.println("[ContactController] POST /api/contacts/add  userId=" + userId + "  contactUsername=" + contactUsername);
        return contactService.addContact(userId, contactUsername.trim(), phone, relation);
    }

    @GetMapping("/api/contacts/list")
    public Result<?> listContacts(@RequestParam(required = false) Long userId) {
        if (userId == null) userId = 1L;
        return contactService.listByUserId(userId);
    }

    @DeleteMapping("/api/contacts/delete")
    public Result<?> deleteContact(@RequestParam Long id,
                                   @RequestParam(required = false) Long userId) {
        if (userId == null) userId = 1L;
        return contactService.deleteContact(id, userId);
    }

    // ═══════════════════════════════════════════
    //  消息 / 求助 — /api/messages/*
    // ═══════════════════════════════════════════

    @PostMapping("/api/messages/help")
    public Result<?> sendHelp(@RequestBody Map<String, Object> body) {
        Long userId = getLong(body, "userId", "currentUserId");
        if (userId == null) userId = 1L;

        String helpType = getString(body, "helpType", "eventType");
        String location = getString(body, "location");
        String description = getString(body, "description", "notes", "message");
        String deviceCode = getString(body, "deviceCode");

        System.out.println("[ContactController] POST /api/messages/help  userId=" + userId + "  type=" + helpType);
        return contactService.sendHelp(userId, helpType, location, description, deviceCode);
    }

    @PostMapping("/api/messages/send")
    public Result<?> sendMessage(@RequestBody Map<String, Object> body) {
        Long senderId = getLong(body, "senderId", "currentUserId");
        if (senderId == null) senderId = 1L;

        String content = getString(body, "content");
        if (content == null || content.isBlank()) {
            return Result.fail(400, "消息内容不能为空");
        }

        String receiverUsername = getString(body, "receiverUsername", "targetUsername");
        if (receiverUsername != null && !receiverUsername.isBlank()) {
            Map<String, Object> r = msgService.sendByUsername(senderId, receiverUsername, content, "TEXT");
            int code = (int) r.getOrDefault("code", 500);
            String msg = (String) r.getOrDefault("message", "发送失败");
            if (code == 200) return Result.ok(msg, r.get("data"));
            return Result.fail(code, msg);
        }

        Long receiverId = getLong(body, "receiverId", "targetUserId");
        if (receiverId == null) {
            return Result.fail(400, "请指定接收用户");
        }
        Map<String, Object> r = msgService.send(senderId, receiverId, "", content, "TEXT");
        int code = (int) r.getOrDefault("code", 500);
        String msg = (String) r.getOrDefault("message", "发送失败");
        if (code == 200) return Result.ok(msg, r.get("data"));
        return Result.fail(code, msg);
    }

    @GetMapping("/api/messages/conversation")
    public Result<?> getConversation(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) String targetUsername) {
        if (userId == null) userId = 1L;
        if (targetUserId == null && targetUsername != null) {
            targetUserId = msgService.resolveUserIdByUsername(targetUsername);
        }
        if (targetUserId == null) {
            return Result.fail(400, "请指定目标用户");
        }
        List<UserMessage> messages = msgService.getConversation(userId, targetUserId);
        msgService.markReadBySender(userId, targetUserId);
        return Result.ok("success", messages);
    }

    @GetMapping("/api/messages/conversations")
    public Result<?> getConversationList(@RequestParam(required = false) Long userId) {
        if (userId == null) userId = 1L;
        return Result.ok("success", msgService.getConversationList(userId));
    }

    @GetMapping("/api/users/search")
    public Result<?> searchUsers(@RequestParam(required = false) String username) {
        return Result.ok("success", msgService.searchUsers(username));
    }

    // ═══════════════════════════════════════════
    //  helper
    // ═══════════════════════════════════════════

    private Long getLong(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value == null) continue;
            try {
                if (value instanceof Number) return ((Number) value).longValue();
                return Long.parseLong(value.toString());
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String getString(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null) {
                String s = value.toString().trim();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }
}
