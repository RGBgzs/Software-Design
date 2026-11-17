package com.example.security.service;

import com.example.security.entity.User;
import com.example.security.entity.UserMessage;
import com.example.security.mapper.UserMapper;
import com.example.security.mapper.UserMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserMessageService {
    @Autowired private UserMapper userMapper;
    @Autowired private UserMessageMapper msgMapper;

    public List<User> getUsers(){return userMapper.selectAllActive();}

    public Map<String,Object> send(Long senderId,Long receiverId,String title,String content,String type){
        Map<String,Object> r=new HashMap<>();
        User sender=userMapper.selectById(senderId);
        User receiver=userMapper.selectById(receiverId);
        if(sender==null||receiver==null){r.put("code",404);r.put("message","用户不存在");return r;}
        UserMessage m=new UserMessage();
        m.setSenderId(senderId);m.setSenderName(sender.getRealName()!=null?sender.getRealName():sender.getUsername());
        m.setReceiverId(receiverId);m.setReceiverName(receiver.getRealName()!=null?receiver.getRealName():receiver.getUsername());
        m.setTitle(title);m.setContent(content);m.setMessageType(type!=null?type:"NORMAL");
        msgMapper.insertMessage(m);
        r.put("code",200);r.put("message","消息发送成功");r.put("data",m);return r;
    }

    /** 通过 username 查找用户 ID */
    public Long resolveUserIdByUsername(String username) {
        if (username == null || username.isBlank()) return null;
        List<User> all = userMapper.selectAllActive();
        for (User u : all) {
            if (username.equals(u.getUsername())) return u.getId();
        }
        return null;
    }

    /** 通过 username 发送消息 */
    public Map<String,Object> sendByUsername(Long senderId, String receiverUsername, String content, String type){
        Map<String,Object> r=new HashMap<>();
        Long receiverId = resolveUserIdByUsername(receiverUsername);
        if (receiverId == null) { r.put("code",404);r.put("message","接收用户不存在");return r; }
        return send(senderId, receiverId, "", content, type);
    }

    public List<UserMessage> inbox(Long userId){return msgMapper.selectInbox(userId);}
    public List<UserMessage> outbox(Long userId){return msgMapper.selectOutbox(userId);}
    public int unreadCount(Long userId){return msgMapper.countUnread(userId);}

    public Map<String,Object> markRead(Long id){
        Map<String,Object> r=new HashMap<>();msgMapper.markRead(id);r.put("code",200);r.put("message","已标记已读");return r;
    }
    public Map<String,Object> delete(Long id,Long userId){
        Map<String,Object> r=new HashMap<>();
        UserMessage m=msgMapper.selectById(id);
        if(m==null){r.put("code",404);r.put("message","消息不存在");return r;}
        if(m.getSenderId().equals(userId))msgMapper.deleteBySender(id);
        else if(m.getReceiverId().equals(userId))msgMapper.deleteByReceiver(id);
        r.put("code",200);r.put("message","已删除");return r;
    }

    /** 获取两个用户之间的对话 */
    public List<UserMessage> getConversation(Long userId, Long targetUserId) {
        return msgMapper.selectConversation(userId, targetUserId);
    }

    /** 获取会话列表 */
    public List<UserMessage> getConversationList(Long userId) {
        return msgMapper.selectConversationList(userId);
    }

    /** 批量标记来自某用户的消息为已读 */
    public void markReadBySender(Long receiverId, Long senderId) {
        msgMapper.markReadBySender(receiverId, senderId);
    }

    /** 搜索用户 */
    public List<Map<String, Object>> searchUsers(String username) {
        List<User> all = userMapper.selectAllActive();
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (User u : all) {
            if (username == null || username.isEmpty() || u.getUsername().contains(username)) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", u.getId());
                item.put("username", u.getUsername());
                item.put("realName", u.getRealName());
                item.put("role", u.getRole());
                result.add(item);
            }
        }
        return result;
    }
}
