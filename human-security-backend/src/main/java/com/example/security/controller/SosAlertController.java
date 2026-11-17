package com.example.security.controller;

import com.example.security.entity.SosAlert;
import com.example.security.mapper.SosAlertMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class SosAlertController {

    @Autowired
    private SosAlertMapper sosAlertMapper;

    /** 保安：获取自己的报警记录 */
    @GetMapping("/sos/alerts")
    public Map<String, Object> getGuardAlerts(@RequestParam Long guardId) {
        List<SosAlert> alerts = sosAlertMapper.findByGuardId(guardId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", alerts);
        result.put("total", alerts.size());
        return result;
    }

    /** 用户：获取自己发起的报警记录 */
    @GetMapping("/sos/my-alerts")
    public Map<String, Object> getMyAlerts(@RequestParam Long userId) {
        List<SosAlert> alerts = sosAlertMapper.findByUserId(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", alerts);
        result.put("total", alerts.size());
        return result;
    }

    /** 保安：处理报警（标记为已处理） */
    @PostMapping("/sos/{id}/handle")
    public Map<String, Object> handleAlert(@PathVariable Long id, @RequestParam(defaultValue = "FINISHED") String status) {
        sosAlertMapper.updateStatus(id, status);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "已处理");
        return result;
    }
}
