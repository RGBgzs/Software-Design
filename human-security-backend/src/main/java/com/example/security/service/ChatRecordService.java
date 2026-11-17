package com.example.security.service;

import com.example.security.entity.ChatRecord;
import com.example.security.mapper.ChatRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatRecordService {

    @Autowired
    private ChatRecordMapper chatRecordMapper;

    /**
     * 查询聊天记录列表，支持可选关键词搜索
     */
    public List<ChatRecord> list(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return chatRecordMapper.selectByKeyword(keyword.trim());
        }
        return chatRecordMapper.selectAll();
    }
}
