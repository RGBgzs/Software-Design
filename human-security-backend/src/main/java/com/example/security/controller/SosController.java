package com.example.security.controller;

import com.example.security.common.Result;
import com.example.security.dto.SosRequestDto;
import com.example.security.service.SosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * SOS 紧急求助 Controller
 *
 * 路径：
 *   POST   /api/messages/sos/send         发送结构化紧急求助
 *   GET    /api/messages/sos/list         求助事件列表
 *   GET    /api/messages/sos/detail/{id}  求助事件详情
 *   POST   /api/messages/sos/acknowledge  确认收到
 *   POST   /api/messages/sos/finish       结束求助
 *   POST   /api/messages/sos/false-alarm  标记误报
 *   POST   /api/messages/read             批量标记已读
 */
@RestController
public class SosController {

    @Autowired private SosService sosService;

    /** 发送结构化紧急求助 */
    @PostMapping("/api/messages/sos/send")
    public Result<?> sendSos(@RequestBody Map<String, Object> body) {
        Long userId = getLong(body, "userId", "currentUserId");
        if (userId == null) userId = 1L;

        SosRequestDto dto = new SosRequestDto();
        dto.setEventType(getString(body, "eventType"));
        dto.setUrgency(getString(body, "urgency"));
        dto.setNotes(getString(body, "notes"));
        dto.setLocation(getString(body, "location"));
        dto.setLinkedPirAlarmId(getLong(body, "linkedPirAlarmId"));
        dto.setLinkedDeviceCode(getString(body, "linkedDeviceCode"));
        dto.setNotifiedGuard(getBoolean(body, "notifiedGuard"));
        dto.setReceiverIds(getLongList(body, "receiverIds"));

        System.out.println("[SosController] POST /api/messages/sos/send  userId=" + userId + "  eventType=" + dto.getEventType());
        return sosService.sendSos(userId, dto);
    }

    /** 求助事件列表 */
    @GetMapping("/api/messages/sos/list")
    public Result<?> listSos(@RequestParam(required = false) Long userId) {
        if (userId == null) userId = 1L;
        return sosService.getSosList(userId);
    }

    /** 求助事件详情 */
    @GetMapping("/api/messages/sos/detail/{id}")
    public Result<?> getSosDetail(@PathVariable Long id) {
        return sosService.getSosDetail(id);
    }

    /** 确认收到求助 */
    @PostMapping("/api/messages/sos/acknowledge")
    public Result<?> acknowledge(@RequestBody Map<String, Object> body) {
        Long eventId = getLong(body, "eventId");
        Long receiverId = getLong(body, "receiverId", "userId", "currentUserId");
        if (eventId == null) return Result.fail(400, "事件ID不能为空");
        if (receiverId == null) receiverId = 1L;
        System.out.println("[SosController] POST /api/messages/sos/acknowledge  eventId=" + eventId + "  receiverId=" + receiverId);
        return sosService.acknowledge(eventId, receiverId);
    }

    /** 结束求助 */
    @PostMapping("/api/messages/sos/finish")
    public Result<?> finish(@RequestBody Map<String, Object> body) {
        Long eventId = getLong(body, "eventId");
        Long userId = getLong(body, "userId", "currentUserId");
        if (eventId == null) return Result.fail(400, "事件ID不能为空");
        if (userId == null) userId = 1L;
        System.out.println("[SosController] POST /api/messages/sos/finish  eventId=" + eventId);
        return sosService.finish(eventId, userId);
    }

    /** 标记误报 */
    @PostMapping("/api/messages/sos/false-alarm")
    public Result<?> falseAlarm(@RequestBody Map<String, Object> body) {
        Long eventId = getLong(body, "eventId");
        Long userId = getLong(body, "userId", "currentUserId");
        if (eventId == null) return Result.fail(400, "事件ID不能为空");
        if (userId == null) userId = 1L;
        System.out.println("[SosController] POST /api/messages/sos/false-alarm  eventId=" + eventId);
        return sosService.markFalseAlarm(eventId, userId);
    }

    /** 批量标记消息已读 */
    @PostMapping("/api/messages/read")
    public Result<?> batchMarkRead(@RequestBody Map<String, Object> body) {
        List<Long> ids = getLongList(body, "ids", "messageIds");
        return sosService.batchMarkRead(ids);
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

    private Boolean getBoolean(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value == null) continue;
            if (value instanceof Boolean) return (Boolean) value;
            String s = value.toString().trim();
            if ("true".equalsIgnoreCase(s)) return true;
            if ("false".equalsIgnoreCase(s)) return false;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Long> getLongList(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value == null) continue;
            if (value instanceof List) {
                List<Long> result = new java.util.ArrayList<>();
                for (Object item : (List<?>) value) {
                    try {
                        if (item instanceof Number) result.add(((Number) item).longValue());
                        else result.add(Long.parseLong(item.toString()));
                    } catch (Exception ignored) {}
                }
                return result.isEmpty() ? null : result;
            }
        }
        return null;
    }
}
