package com.example.security.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.security.entity.Knowledge;
import com.example.security.mapper.KnowledgeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class KnowledgeManageService {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    /**
     * 查询知识库列表（支持关键词搜索）
     */
    public List<Knowledge> list(String keyword) {
        QueryWrapper<Knowledge> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w
                .like("question", keyword)
                .or()
                .like("keywords", keyword)
                .or()
                .like("category", keyword)
                .or()
                .like("answer", keyword)
            );
        }
        wrapper.orderByDesc("id");
        return knowledgeMapper.selectList(wrapper);
    }

    /**
     * 新增知识
     */
    public Map<String, Object> add(Knowledge k) {
        Map<String, Object> result = new HashMap<>();
        if (k.getQuestion() == null || k.getQuestion().trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "问题不能为空");
            return result;
        }
        if (k.getEnabled() == null) {
            k.setEnabled(1);
        }
        if (k.getCreatedAt() == null) {
            k.setCreatedAt(LocalDateTime.now());
        }
        knowledgeMapper.insert(k);
        result.put("success", true);
        result.put("message", "新增成功");
        result.put("id", k.getId());
        return result;
    }

    /**
     * 修改知识
     */
    public Map<String, Object> update(Knowledge k) {
        Map<String, Object> result = new HashMap<>();
        if (k.getId() == null) {
            result.put("success", false);
            result.put("message", "ID 不能为空");
            return result;
        }
        Knowledge exist = knowledgeMapper.selectById(k.getId());
        if (exist == null) {
            result.put("success", false);
            result.put("message", "知识条目不存在");
            return result;
        }
        knowledgeMapper.updateById(k);
        result.put("success", true);
        result.put("message", "修改成功");
        return result;
    }

    /**
     * 删除知识
     */
    public Map<String, Object> delete(Long id) {
        Map<String, Object> result = new HashMap<>();
        if (id == null) {
            result.put("success", false);
            result.put("message", "ID 不能为空");
            return result;
        }
        Knowledge exist = knowledgeMapper.selectById(id);
        if (exist == null) {
            result.put("success", false);
            result.put("message", "知识条目不存在");
            return result;
        }
        knowledgeMapper.deleteById(id);
        result.put("success", true);
        result.put("message", "删除成功");
        return result;
    }

    /**
     * 启用/停用
     */
    public Map<String, Object> toggle(Long id) {
        Map<String, Object> result = new HashMap<>();
        if (id == null) {
            result.put("success", false);
            result.put("message", "ID 不能为空");
            return result;
        }
        Knowledge exist = knowledgeMapper.selectById(id);
        if (exist == null) {
            result.put("success", false);
            result.put("message", "知识条目不存在");
            return result;
        }
        int newEnabled = (exist.getEnabled() != null && exist.getEnabled() == 1) ? 0 : 1;
        Knowledge update = new Knowledge();
        update.setId(id);
        update.setEnabled(newEnabled);
        knowledgeMapper.updateById(update);
        result.put("success", true);
        result.put("message", newEnabled == 1 ? "已启用" : "已停用");
        result.put("enabled", newEnabled);
        return result;
    }
}
