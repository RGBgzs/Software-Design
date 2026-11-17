package com.example.security.controller;

import com.example.security.entity.AlarmRecord;
import com.example.security.entity.Device;
import com.example.security.entity.HumanRecord;
import com.example.security.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    // 1. 设备实时状态查询
    @GetMapping("/devices/{deviceCode}/status")
    public Map<String, Object> getDeviceStatus(@PathVariable String deviceCode) {
        Map<String, Object> status = deviceService.getDeviceStatus(deviceCode);
        if (status == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("code", 404);
            err.put("message", "设备不存在");
            return err;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", status);
        return result;
    }

    // 2. 人体感应历史记录
    @GetMapping("/records")
    public Map<String, Object> getRecords(@RequestParam(defaultValue = "50") Integer limit) {
        List<HumanRecord> records = deviceService.getRecords(limit);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", records);
        result.put("total", records.size());
        return result;
    }

    // 3. 告警记录查询（支持按角色过滤）
    @GetMapping("/alarms")
    public Map<String, Object> getAlarms(
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false, defaultValue = "admin") String role) {
        List<AlarmRecord> alarms = deviceService.getAlarms(limit, userId, role);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", alarms);
        result.put("total", alarms.size());
        return result;
    }

    // 4. 布防
    @PostMapping("/devices/{deviceCode}/defense/on")
    public Map<String, Object> defenseOn(@PathVariable String deviceCode,
                                         @RequestBody(required = false) Map<String, String> body) {
        String operator = (body != null && body.get("operator") != null) ? body.get("operator") : "admin";
        Map<String, Object> svcResult = deviceService.defenseOn(deviceCode, operator);
        Map<String, Object> result = new HashMap<>();
        boolean success = svcResult.get("success") != null && (Boolean) svcResult.get("success");
        result.put("code", success ? 200 : 400);
        result.put("success", success);
        result.put("message", svcResult.get("message"));
        result.put("deviceCode", deviceCode);
        result.put("command", "DEFENSE_ON");
        if (svcResult.get("commandId") != null) {
            result.put("commandId", svcResult.get("commandId"));
        }
        return result;
    }

    // 5. 撤防
    @PostMapping("/devices/{deviceCode}/defense/off")
    public Map<String, Object> defenseOff(@PathVariable String deviceCode,
                                          @RequestBody(required = false) Map<String, String> body) {
        String operator = (body != null && body.get("operator") != null) ? body.get("operator") : "admin";
        Map<String, Object> svcResult = deviceService.defenseOff(deviceCode, operator);
        Map<String, Object> result = new HashMap<>();
        boolean success = svcResult.get("success") != null && (Boolean) svcResult.get("success");
        result.put("code", success ? 200 : 400);
        result.put("success", success);
        result.put("message", svcResult.get("message"));
        result.put("deviceCode", deviceCode);
        result.put("command", "DEFENSE_OFF");
        if (svcResult.get("commandId") != null) {
            result.put("commandId", svcResult.get("commandId"));
        }
        return result;
    }

    // ==================== 设备管理 CRUD ====================

    // 6. 设备列表（支持按角色过滤）
    @GetMapping("/devices")
    public Map<String, Object> getDevices(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false, defaultValue = "admin") String role) {
        List<Device> devices = deviceService.getDevices(userId, role);
        Map<String, Object> pageData = new HashMap<>();
        pageData.put("records", devices);
        pageData.put("total", devices.size());
        pageData.put("page", 1);
        pageData.put("pageSize", devices.size());

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", pageData);
        result.put("total", devices.size());
        return result;
    }

    // 7. 设备统计（来自 device 表）
    @GetMapping("/devices/stats")
    public Map<String, Object> getDeviceStats() {
        Map<String, Object> stats = deviceService.getDeviceStats();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("success", true);
        result.put("message", "查询成功");
        result.put("data", stats);
        return result;
    }

    // 8. 新增设备
    @PostMapping("/devices")
    public Map<String, Object> addDevice(@RequestBody Device device) {
        boolean ok = deviceService.addDevice(device);
        Map<String, Object> result = new HashMap<>();
        result.put("code", ok ? 200 : 500);
        result.put("message", ok ? "设备添加成功" : "设备编号已存在");
        return result;
    }

    // 9. 更新设备
    @PutMapping("/devices/{id}")
    public Map<String, Object> updateDevice(@PathVariable Long id, @RequestBody Device device) {
        device.setId(id);
        boolean ok = deviceService.updateDevice(device);
        Map<String, Object> result = new HashMap<>();
        result.put("code", ok ? 200 : 500);
        result.put("message", ok ? "设备更新成功" : "设备不存在");
        return result;
    }

    // 10. 删除设备
    @DeleteMapping("/devices/{id}")
    public Map<String, Object> deleteDevice(@PathVariable Long id) {
        boolean ok = deviceService.deleteDevice(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", ok ? 200 : 500);
        result.put("message", ok ? "设备已删除" : "设备不存在");
        return result;
    }

    // ==================== 告警处理 ====================

    // 11. 确认处理告警（用户和保安均可操作）
    @PostMapping("/alarms/{id}/handle")
    public Map<String, Object> handleAlarm(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Integer handleStatus = null;
        try {
            String hs = body.get("handleStatus");
            if (hs != null) handleStatus = Integer.parseInt(hs);
        } catch (NumberFormatException ignored) {}
        if (handleStatus == null) handleStatus = 1;

        String handleUser = body.get("handleUser");
        String remark = body.get("remark");

        boolean ok = deviceService.handleAlarm(id, handleStatus, handleUser, remark);
        Map<String, Object> result = new HashMap<>();
        result.put("code", ok ? 200 : 500);
        String actionLabel = handleStatus == 2 ? "已标记为误报" : "告警已处理";
        result.put("message", ok ? actionLabel : "告警不存在");
        return result;
    }
}
