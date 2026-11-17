package com.example.security.service;

import com.example.security.entity.EmergencyContact;
import com.example.security.entity.SosAlert;
import com.example.security.entity.User;
import com.example.security.entity.UserMessage;
import com.example.security.mapper.EmergencyContactMapper;
import com.example.security.mapper.SosAlertMapper;
import com.example.security.mapper.UserMapper;
import com.example.security.mapper.UserMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import java.util.*;

@Service
public class EmergencyContactService {
    @Autowired private EmergencyContactMapper contactMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private UserMessageMapper msgMapper;
    @Autowired private SosAlertMapper sosAlertMapper;

    /** 获取我的紧急联系人列表 */
    public List<EmergencyContact> getMyContacts(Long userId) {
        return contactMapper.findByUserId(userId);
    }

    /** 通过 username 添加紧急联系人 */
    public Map<String, Object> addContact(Long userId, String contactUsername) {
        Map<String, Object> r = new HashMap<>();

        // 查找目标用户
        User target = findUserByUsername(contactUsername);
        if (target == null) {
            r.put("code", 404); r.put("message", "用户不存在"); return r;
        }

        // 不能添加自己
        if (target.getId().equals(userId)) {
            r.put("code", 400); r.put("message", "不能添加自己为紧急联系人"); return r;
        }

        // 不能重复添加
        EmergencyContact existing = contactMapper.findByUserAndContact(userId, target.getId());
        if (existing != null) {
            r.put("code", 400); r.put("message", "该用户已是您的紧急联系人"); return r;
        }

        EmergencyContact contact = new EmergencyContact();
        contact.setUserId(userId);
        contact.setContactUserId(target.getId());
        contact.setContactUsername(target.getUsername());
        contact.setContactNickname(target.getRealName() != null ? target.getRealName() : target.getUsername());
        contactMapper.insertContact(contact);

        r.put("code", 200); r.put("message", "添加成功"); r.put("data", contact); return r;
    }

    /** 删除紧急联系人 */
    public Map<String, Object> deleteContact(Long id, Long userId) {
        Map<String, Object> r = new HashMap<>();
        EmergencyContact contact = contactMapper.selectById(id);
        if (contact == null || !contact.getUserId().equals(userId)) {
            r.put("code", 404); r.put("message", "联系人不存在"); return r;
        }
        contactMapper.softDelete(id);
        r.put("code", 200); r.put("message", "删除成功"); return r;
    }

    /** 发送紧急求助给所有紧急联系人（含所属保安） */
    public Map<String, Object> sendHelp(Long userId) {
        return sendHelp(userId, null, null, null, null);
    }

    /** 发送紧急求助给所有紧急联系人（含所属保安），附带详情 */
    public Map<String, Object> sendHelp(Long userId, String helpType, String location,
                                         String description, String deviceCode) {
        Map<String, Object> r = new HashMap<>();
        User sender = userMapper.selectById(userId);
        if (sender == null) {
            r.put("code", 404); r.put("message", "用户不存在"); return r;
        }

        String senderName = (sender.getRealName() != null && !sender.getRealName().isEmpty())
                ? sender.getRealName() : sender.getUsername();
        String senderDisplay = (sender.getRealName() != null && sender.getRealName().length() > 1)
                ? sender.getRealName() : sender.getUsername();

        // 构建消息内容
        String helpContent = buildHelpContent(senderName, helpType, location, description, deviceCode);

        List<EmergencyContact> contacts = contactMapper.findByUserId(userId);
        int count = 0;

        // 发送给紧急联系人
        for (EmergencyContact contact : contacts) {
            UserMessage msg = new UserMessage();
            msg.setSenderId(userId);
            msg.setSenderName(senderName);
            msg.setReceiverId(contact.getContactUserId());
            msg.setReceiverName(contact.getContactUsername());
            msg.setTitle("紧急求助" + (helpType != null ? " - " + getHelpTypeLabel(helpType) : ""));
            msg.setContent(helpContent);
            msg.setMessageType("SOS");
            msg.setIsEmergency(1);
            msgMapper.insertMessage(msg);
            count++;
        }

        // ── 新增：自动通知所属保安 ──
        User guard = null;
        if (sender.getGuardId() != null) {
            guard = userMapper.selectById(sender.getGuardId());
            boolean alreadyNotified = contacts.stream()
                    .anyMatch(c -> c.getContactUserId().equals(sender.getGuardId()));
            if (!alreadyNotified && guard != null) {
                UserMessage guardMsg = new UserMessage();
                guardMsg.setSenderId(userId);
                guardMsg.setSenderName(senderName);
                guardMsg.setReceiverId(guard.getId());
                guardMsg.setReceiverName(guard.getUsername());
                guardMsg.setTitle("紧急求助 - 居民 " + senderName + " 求助");
                guardMsg.setContent(helpContent);
                guardMsg.setMessageType("SOS");
                guardMsg.setIsEmergency(1);
                msgMapper.insertMessage(guardMsg);
                count++;
            }
        }

        // ── 保存报警记录到 sos_alert 表 ──
        SosAlert alert = new SosAlert();
        alert.setUserId(userId);
        alert.setUserName(senderDisplay);
        alert.setGuardId(sender.getGuardId());
        alert.setGuardName(guard != null ? guard.getRealName() : null);
        alert.setHelpType(helpType);
        alert.setLocation(location);
        alert.setDescription(description);
        alert.setDeviceCode(deviceCode);
        alert.setStatus("SENT");
        sosAlertMapper.insert(alert);

        // 写入发送者自己的消息记录
        UserMessage selfMsg = new UserMessage();
        selfMsg.setSenderId(userId);
        selfMsg.setSenderName(senderName);
        selfMsg.setReceiverId(userId);
        selfMsg.setReceiverName(senderName);
        selfMsg.setTitle("紧急求助已发送");
        selfMsg.setContent("你已向 " + count + " 位联系人发送紧急求助。");
        selfMsg.setMessageType("SOS");
        selfMsg.setIsEmergency(1);
        msgMapper.insertMessage(selfMsg);

        r.put("code", 200);
        r.put("message", "求助信息已发送给 " + count + " 位联系人，请保持冷静。");
        r.put("count", count);
        return r;
    }

    private String buildHelpContent(String senderName, String helpType, String location,
                                     String description, String deviceCode) {
        StringBuilder sb = new StringBuilder();
        sb.append("【紧急求助】\n");
        sb.append("发起人：").append(senderName).append("\n");
        if (helpType != null) {
            sb.append("求助类型：").append(getHelpTypeLabel(helpType)).append("\n");
        }
        if (location != null && !location.isEmpty()) {
            sb.append("位置：").append(location).append("\n");
        }
        if (deviceCode != null && !deviceCode.isEmpty()) {
            sb.append("关联设备：").append(deviceCode).append("\n");
        }
        if (description != null && !description.isEmpty()) {
            sb.append("补充说明：").append(description).append("\n");
        }
        sb.append("\n请尽快响应此求助！");
        return sb.toString();
    }

    private String getHelpTypeLabel(String type) {
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

    private User findUserByUsername(String username) {
        List<User> users = userMapper.selectAllActive();
        for (User u : users) {
            if (u.getUsername().equals(username)) return u;
        }
        return null;
    }
}
