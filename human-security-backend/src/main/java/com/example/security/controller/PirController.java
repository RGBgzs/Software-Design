package com.example.security.controller;

import com.example.security.service.PirService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * PIR 人体感应监测控制器
 * 提供 PIR 设备状态、记录、统计、布防/撤防等接口
 */
@RestController
@RequestMapping("/api/pir")
public class PirController {

    @Autowired
    private PirService pirService;

    /**
     * GET /api/pir/status
     * 获取所有 PIR 设备实时状态
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        List<Map<String, Object>> devices = pirService.getPirDeviceStatus();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", devices);
        result.put("total", devices.size());
        return result;
    }

    /**
     * GET /api/pir/records
     * 获取 PIR 人体感应记录（分页）
     */
    @GetMapping("/records")
    public Map<String, Object> getRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return pirService.getPirRecords(page, pageSize, deviceCode, startTime, endTime);
    }

    /**
     * POST /api/pir/report
     * 手动上报 PIR 数据（模拟设备上报或 HTTP 接口）
     */
    @PostMapping("/report")
    public Map<String, Object> report(@RequestBody Map<String, Object> body) {
        String deviceCode = (String) body.get("deviceCode");
        Integer humanDetected = body.get("humanDetected") != null ? ((Number) body.get("humanDetected")).intValue() : 0;
        Integer defenseStatus = body.get("defenseStatus") != null ? ((Number) body.get("defenseStatus")).intValue() : 0;

        boolean ok = pirService.reportPirData(deviceCode, humanDetected, defenseStatus, body);
        Map<String, Object> result = new HashMap<>();
        result.put("code", ok ? 200 : 500);
        result.put("message", ok ? "上报成功" : "上报失败");
        return result;
    }

    /**
     * GET /api/pir/stats
     * PIR 统计数据概览
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = pirService.getPirStats();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", stats);
        return result;
    }

    /**
     * POST /api/pir/arm
     * PIR 开启布防
     */
    @PostMapping("/arm")
    public Map<String, Object> arm(@RequestBody Map<String, Object> body) {
        String deviceCode = (String) body.get("deviceCode");
        String operator = (String) body.getOrDefault("operator", "admin");

        boolean ok = pirService.armDevice(deviceCode, operator);
        Map<String, Object> result = new HashMap<>();
        result.put("code", ok ? 200 : 500);
        result.put("message", ok ? "布防指令已下发" : "布防失败");
        result.put("deviceCode", deviceCode);
        return result;
    }

    /**
     * POST /api/pir/disarm
     * PIR 撤销布防
     */
    @PostMapping("/disarm")
    public Map<String, Object> disarm(@RequestBody Map<String, Object> body) {
        String deviceCode = (String) body.get("deviceCode");
        String operator = (String) body.getOrDefault("operator", "admin");

        boolean ok = pirService.disarmDevice(deviceCode, operator);
        Map<String, Object> result = new HashMap<>();
        result.put("code", ok ? 200 : 500);
        result.put("message", ok ? "撤防指令已下发" : "撤防失败");
        result.put("deviceCode", deviceCode);
        return result;
    }

    /**
     * GET /api/pir/today-count
     * 今日人体感应次数
     */
    @GetMapping("/today-count")
    public Map<String, Object> getTodayCount() {
        long count = pirService.getTodayDetectionCount();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", count);
        return result;
    }

    /**
     * GET /api/pir/latest
     * 最近一次感应时间
     */
    @GetMapping("/latest")
    public Map<String, Object> getLatest() {
        Map<String, Object> latest = pirService.getLatestDetection();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", latest);
        return result;
    }
}
