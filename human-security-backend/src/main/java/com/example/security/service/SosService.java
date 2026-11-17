package com.example.security.service;

import com.example.security.common.Result;
import com.example.security.dto.SosRequestDto;
import com.example.security.entity.*;
import com.example.security.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SosService {

    private static final Logger log = LoggerFactory.getLogger(SosService.class);

    @Autowired private EmergencyHelpEventMapper eventMapper;
    @Autowired private EmergencyHelpReceiverMapper receiverMapper;
    @Autowired private UserMessageMapper msgMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private EmergencyContactMapper contactMapper;

    /** 发送结构化紧急求助 */
    public Result<?> sendSos(Long userId, SosRequestDto dto) {
        User sender = userMapper.selectById(userId);
        if (sender == null) {
            return Result.fail(404, "用户不存在");
        }

        String senderName = sender.getRealName() != null ? sender.getRealName() : sender.getUsername();

        // 1. 创建求助事件
        EmergencyHelpEvent event = new EmergencyHelpEvent();
        event.setInitiatorId(userId);
        event.setEventType(dto.getEventType() != null ? dto.getEventType() : "SECURITY");
        event.setUrgency(dto.getUrgency() != null ? dto.getUrgency() : "HIGH");
        event.setStatus("SENT");
        event.setLocation(dto.getLocation());
        event.setNotes(dto.getNotes());
        event.setLinkedPirAlarmId(dto.getLinkedPirAlarmId());
        event.setLinkedDeviceCode(dto.getLinkedDeviceCode());
        eventMapper.insertEvent(event);

        // 2. 确定接收人列表
        List<EmergencyContact> contacts;
        if (dto.getReceiverIds() != null && !dto.getReceiverIds().isEmpty()) {
            // 使用指定的接收人
            contacts = new ArrayList<>();
            for (Long contactUserId : dto.getReceiverIds()) {
                EmergencyContact ec = contactMapper.findByUserAndContact(userId, contactUserId);
                if (ec != null && ec.getStatus() == 1) {
                    contacts.add(ec);
                }
            }
        } else {
            // 默认发送给所有紧急联系人
            contacts = contactMapper.findByUserId(userId);
        }

        if (contacts.isEmpty()) {
            return Result.fail(400, "没有可用的紧急联系人");
        }

        // 3. 为每个接收人创建 receiver 记录 + SOS 消息
        int receiverCount = 0;
        for (EmergencyContact contact : contacts) {
            EmergencyHelpReceiver receiver = new EmergencyHelpReceiver();
            receiver.setEventId(event.getId());
            receiver.setReceiverId(contact.getContactUserId());
            receiver.setReceiverName(contact.getContactUsername());
            receiver.setStatus("SENT");
            receiverMapper.insertReceiver(receiver);

            // 发送 SOS 消息给接收人
            UserMessage msg = new UserMessage();
            msg.setSenderId(userId);
            msg.setSenderName(senderName);
            msg.setReceiverId(contact.getContactUserId());
            msg.setReceiverName(contact.getContactUsername());
            msg.setTitle("紧急求助 - " + getEventTypeLabel(event.getEventType()));
            msg.setContent(buildSosContent(senderName, event, dto));
            msg.setMessageType("SOS");
            msg.setIsEmergency(1);
            msgMapper.insertMessage(msg);
            receiverCount++;
        }

        // 4. 给发起人写入 SYSTEM 发送记录
        UserMessage selfMsg = new UserMessage();
        selfMsg.setSenderId(userId);
        selfMsg.setSenderName(senderName);
        selfMsg.setReceiverId(userId);
        selfMsg.setReceiverName(senderName);
        selfMsg.setTitle("紧急求助已发送");
        selfMsg.setContent("你已向 " + receiverCount + " 位紧急联系人发送紧急求助（" + getEventTypeLabel(event.getEventType()) + "），请保持冷静等待响应。");
        selfMsg.setMessageType("SYSTEM");
        selfMsg.setIsEmergency(1);
        msgMapper.insertMessage(selfMsg);

        // 5. 如果通知值班保安，给保安角色的用户也发送消息
        if (Boolean.TRUE.equals(dto.getNotifiedGuard())) {
            List<User> allUsers = userMapper.selectAllActive();
            for (User u : allUsers) {
                if ("guard".equals(u.getRole())) {
                    UserMessage guardMsg = new UserMessage();
                    guardMsg.setSenderId(userId);
                    guardMsg.setSenderName(senderName);
                    guardMsg.setReceiverId(u.getId());
                    guardMsg.setReceiverName(u.getUsername());
                    guardMsg.setTitle("紧急求助通知 - " + getEventTypeLabel(event.getEventType()));
                    guardMsg.setContent(buildSosContent(senderName, event, dto));
                    guardMsg.setMessageType("SOS");
                    guardMsg.setIsEmergency(1);
                    msgMapper.insertMessage(guardMsg);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", event.getId());
        result.put("receiverCount", receiverCount);
        result.put("status", event.getStatus());

        log.info("[SosService] 紧急求助发送成功: eventId={}, userId={}, receivers={}", event.getId(), userId, receiverCount);
        return Result.ok("求助已发送给 " + receiverCount + " 位联系人", result);
    }

    /** 获取 SOS 事件列表（发起 + 接收） */
    public Result<?> getSosList(Long userId) {
        List<EmergencyHelpEvent> initiated = eventMapper.findByInitiator(userId);
        List<EmergencyHelpEvent> received = eventMapper.findByReceiver(userId);

        // 合并去重
        Map<Long, EmergencyHelpEvent> merged = new LinkedHashMap<>();
        for (EmergencyHelpEvent e : initiated) merged.put(e.getId(), e);
        for (EmergencyHelpEvent e : received) merged.putIfAbsent(e.getId(), e);

        List<Map<String, Object>> list = new ArrayList<>();
        for (EmergencyHelpEvent e : merged.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("initiatorId", e.getInitiatorId());
            item.put("eventType", e.getEventType());
            item.put("urgency", e.getUrgency());
            item.put("status", e.getStatus());
            item.put("location", e.getLocation());
            item.put("notes", e.getNotes());
            item.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
            item.put("isInitiator", e.getInitiatorId().equals(userId));
            list.add(item);
        }

        // 按时间倒序
        list.sort((a, b) -> {
            String ta = (String) a.getOrDefault("createdAt", "");
            String tb = (String) b.getOrDefault("createdAt", "");
            return tb.compareTo(ta);
        });

        return Result.ok("success", list);
    }

    /** 获取 SOS 事件详情 */
    public Result<?> getSosDetail(Long eventId) {
        EmergencyHelpEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            return Result.fail(404, "求助事件不存在");
        }

        List<EmergencyHelpReceiver> receivers = receiverMapper.findByEventId(eventId);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("event", event);
        detail.put("receivers", receivers);

        return Result.ok("success", detail);
    }

    /** 接收人确认收到 */
    public Result<?> acknowledge(Long eventId, Long receiverId) {
        EmergencyHelpReceiver receiver = receiverMapper.findByEventAndReceiver(eventId, receiverId);
        if (receiver == null) {
            return Result.fail(404, "接收记录不存在");
        }

        receiverMapper.updateStatus(receiver.getId(), "ACKNOWLEDGED");

        // 更新事件状态
        EmergencyHelpEvent event = eventMapper.selectById(eventId);
        if (event != null && "SENT".equals(event.getStatus())) {
            eventMapper.updateStatus(eventId, "RESPONDING");
        }

        log.info("[SosService] 接收人确认: eventId={}, receiverId={}", eventId, receiverId);
        return Result.ok("已确认收到");
    }

    /** 结束求助 */
    public Result<?> finish(Long eventId, Long userId) {
        EmergencyHelpEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            return Result.fail(404, "求助事件不存在");
        }
        if (!event.getInitiatorId().equals(userId)) {
            return Result.fail(403, "只有发起人可以结束求助");
        }

        eventMapper.updateStatus(eventId, "FINISHED");
        log.info("[SosService] 求助结束: eventId={}, userId={}", eventId, userId);
        return Result.ok("求助已结束");
    }

    /** 标记误报 */
    public Result<?> markFalseAlarm(Long eventId, Long userId) {
        EmergencyHelpEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            return Result.fail(404, "求助事件不存在");
        }
        if (!event.getInitiatorId().equals(userId)) {
            return Result.fail(403, "只有发起人可以标记误报");
        }

        eventMapper.updateStatus(eventId, "FALSE_ALARM");
        log.info("[SosService] 求助标记为误报: eventId={}, userId={}", eventId, userId);
        return Result.ok("已标记为误报");
    }

    /** 批量标记消息已读 */
    public Result<?> batchMarkRead(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.fail(400, "消息ID列表不能为空");
        }
        int count = msgMapper.batchMarkRead(ids);
        return Result.ok("已标记 " + count + " 条消息为已读");
    }

    // ── helpers ──

    private String getEventTypeLabel(String type) {
        if (type == null) return "安全异常";
        switch (type) {
            case "SECURITY": return "安全异常";
            case "SUDDEN_ILLNESS": return "突发疾病";
            case "FIRE": return "火灾危险";
            case "FALL_INJURY": return "跌倒受伤";
            case "OTHER": return "其他";
            default: return type;
        }
    }

    private String buildSosContent(String senderName, EmergencyHelpEvent event, SosRequestDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("【紧急求助】\n");
        sb.append("发起人：").append(senderName).append("\n");
        sb.append("求助类型：").append(getEventTypeLabel(event.getEventType())).append("\n");
        sb.append("紧急程度：");
        String urgency = event.getUrgency();
        if ("HIGH".equals(urgency)) sb.append("🔴 高");
        else if ("MEDIUM".equals(urgency)) sb.append("🟡 中");
        else sb.append("🟢 低");
        sb.append("\n");
        if (event.getLocation() != null && !event.getLocation().isEmpty()) {
            sb.append("位置：").append(event.getLocation()).append("\n");
        }
        if (event.getNotes() != null && !event.getNotes().isEmpty()) {
            sb.append("补充说明：").append(event.getNotes()).append("\n");
        }
        if (event.getLinkedDeviceCode() != null) {
            sb.append("关联设备：").append(event.getLinkedDeviceCode()).append("\n");
        }
        sb.append("\n请尽快响应此求助！");
        return sb.toString();
    }
}
