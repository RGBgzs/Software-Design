package com.example.security.controller;

import com.example.security.entity.Knowledge;
import com.example.security.service.KnowledgeManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeManageController {

    @Autowired
    private KnowledgeManageService knowledgeManageService;

    /**
     * GET /api/kb/list?keyword=xxx
     */
    @GetMapping("/list")
    public List<Knowledge> list(@RequestParam(required = false) String keyword) {
        return knowledgeManageService.list(keyword);
    }

    /**
     * POST /api/kb/add
     */
    @PostMapping("/add")
    public Map<String, Object> add(@RequestBody Knowledge knowledge) {
        return knowledgeManageService.add(knowledge);
    }

    /**
     * PUT /api/kb/update
     */
    @PutMapping("/update")
    public Map<String, Object> update(@RequestBody Knowledge knowledge) {
        return knowledgeManageService.update(knowledge);
    }

    /**
     * DELETE /api/kb/delete/{id}
     */
    @DeleteMapping("/delete/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        return knowledgeManageService.delete(id);
    }

    /**
     * POST /api/kb/toggle/{id}
     */
    @PostMapping("/toggle/{id}")
    public Map<String, Object> toggle(@PathVariable Long id) {
        return knowledgeManageService.toggle(id);
    }
}
