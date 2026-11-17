package com.example.security.service;

import com.example.security.common.Result;
import com.example.security.entity.EmergencyContact;
import com.example.security.entity.User;
import com.example.security.mapper.EmergencyContactMapper;
import com.example.security.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    @Autowired private EmergencyContactMapper contactMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private EmergencyContactService emergencyContactService;

    /** 获取我的紧急联系人列表 */
    public Result<List<Map<String, Object>>> listByUserId(Long userId) {
        try {
            List<EmergencyContact> contacts = contactMapper.findByUserId(userId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (EmergencyContact c : contacts) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", c.getId());
                item.put("userId", c.getUserId());
                item.put("contactUserId", c.getContactUserId());
                item.put("contactUsername", c.getContactUsername());
                item.put("contactNickname", c.getContactNickname());
                item.put("status", c.getStatus());
                item.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
                result.add(item);
            }
            return Result.ok("success", result);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("doesn't exist") || msg.contains("Table") || msg.contains("table")) {
                log.error("[ContactService] 数据表未初始化，请检查数据库迁移是否成功: {}", msg);
                return Result.fail(500, "消息中心数据表未初始化，请检查数据库迁移。如问题持续，请联系管理员。");
            }
            log.error("[ContactService] 查询联系人列表失败: {}", msg);
            return Result.fail(500, "查询联系人列表失败，请稍后重试。");
        }
    }

    /** 添加紧急联系人 */
    public Result<?> addContact(Long userId, String contactUsername, String phone, String relation) {
        if (contactUsername == null || contactUsername.isBlank()) {
            return Result.fail(400, "联系人用户名不能为空");
        }

        // 查找目标用户
        User target = findUserByUsername(contactUsername.trim());
        if (target == null) {
            return Result.fail(404, "用户不存在");
        }

        // 不能添加自己
        if (target.getId().equals(userId)) {
            return Result.fail(400, "不能添加自己为紧急联系人");
        }

        // 不能重复添加
        EmergencyContact existing = contactMapper.findByUserAndContact(userId, target.getId());
        if (existing != null) {
            return Result.fail(400, "该联系人已存在");
        }

        EmergencyContact contact = new EmergencyContact();
        contact.setUserId(userId);
        contact.setContactUserId(target.getId());
        contact.setContactUsername(target.getUsername());
        contact.setContactNickname(
            relation != null && !relation.isBlank()
                ? target.getUsername() + "（" + relation + "）"
                : (target.getRealName() != null ? target.getRealName() : target.getUsername())
        );
        contactMapper.insertContact(contact);

        log.info("[ContactService] 添加联系人成功: userId={}, contactUsername={}", userId, contactUsername);
        return Result.ok("添加成功");
    }

    /** 删除紧急联系人 */
    public Result<?> deleteContact(Long id, Long userId) {
        EmergencyContact contact = contactMapper.selectById(id);
        if (contact == null || !contact.getUserId().equals(userId)) {
            return Result.fail(404, "联系人不存在");
        }
        contactMapper.softDelete(id);
        log.info("[ContactService] 删除联系人成功: id={}, userId={}", id, userId);
        return Result.ok("删除成功");
    }

    /** 发送紧急求助给所有紧急联系人 */
    public Result<?> sendHelp(Long userId) {
        return sendHelp(userId, null, null, null, null);
    }

    /** 发送紧急求助给所有紧急联系人（附带详情） */
    public Result<?> sendHelp(Long userId, String helpType, String location,
                               String description, String deviceCode) {
        Map<String, Object> result = emergencyContactService.sendHelp(userId, helpType, location, description, deviceCode);
        int code = (int) result.getOrDefault("code", 500);
        String message = (String) result.getOrDefault("message", "操作失败");
        if (code == 200) {
            return Result.ok(message, result);
        }
        return Result.fail(code, message);
    }

    private User findUserByUsername(String username) {
        List<User> users = userMapper.selectAllActive();
        for (User u : users) {
            if (u.getUsername().equals(username)) return u;
        }
        return null;
    }
}
