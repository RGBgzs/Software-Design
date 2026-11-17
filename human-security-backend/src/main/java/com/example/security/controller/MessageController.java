package com.example.security.controller;

import com.example.security.entity.User;
import com.example.security.entity.UserMessage;
import com.example.security.service.UserMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 消息收件箱/发件箱/已读/未读/删除
 * 消息发送 /api/messages/send 由 ContactController 处理（支持 receiverUsername + Result 格式）
 */
@RestController
@RequestMapping("/api")
public class MessageController {
    @Autowired private UserMessageService service;

    @GetMapping("/users/list")
    public List<User> listUsers(){return service.getUsers();}

    @GetMapping("/messages/inbox")
    public Map<String,Object> inbox(@RequestParam Long userId){
        List<UserMessage> list=service.inbox(userId);
        Map<String,Object> r=new HashMap<>();r.put("code",200);r.put("total",list.size());r.put("data",list);return r;
    }

    @GetMapping("/messages/outbox")
    public Map<String,Object> outbox(@RequestParam Long userId){
        List<UserMessage> list=service.outbox(userId);
        Map<String,Object> r=new HashMap<>();r.put("code",200);r.put("total",list.size());r.put("data",list);return r;
    }

    @GetMapping("/messages/unread-count")
    public Map<String,Object> unreadCount(@RequestParam Long userId){
        int c=service.unreadCount(userId);
        Map<String,Object> r=new HashMap<>();r.put("code",200);r.put("count",c);return r;
    }

    @PostMapping("/messages/{id}/read")
    public Map<String,Object> markRead(@PathVariable Long id){return service.markRead(id);}

    @PostMapping("/messages/{id}/delete")
    public Map<String,Object> delete(@PathVariable Long id,@RequestParam Long userId){return service.delete(id,userId);}
}
