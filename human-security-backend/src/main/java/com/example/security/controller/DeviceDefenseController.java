package com.example.security.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.security.entity.DeviceStatus;
import com.example.security.mapper.DeviceStatusMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 设备布防/撤防（仅更新数据库，不接 MQTT、不向硬件下发指令）
 */
@RestController
@RequestMapping("/api")
public class DeviceDefenseController {

    @Autowired
    private DeviceStatusMapper deviceStatusMapper;

    @PostMapping("/device/{deviceCode}/defense-on")
    public Map<String, Object> defenseOn(@PathVariable String deviceCode) {
        return setDefense(deviceCode, 1, "布防");
    }

    @PostMapping("/device/{deviceCode}/defense-off")
    public Map<String, Object> defenseOff(@PathVariable String deviceCode) {
        return setDefense(deviceCode, 0, "撤防");
    }

    private Map<String, Object> setDefense(String deviceCode, int status, String action) {
        DeviceStatus ds = deviceStatusMapper.selectOne(
                new QueryWrapper<DeviceStatus>().eq("device_code", deviceCode));
        Map<String, Object> result = new HashMap<>();
        if (ds == null) {
            result.put("code", 404);
            result.put("message", "设备不存在：" + deviceCode);
            return result;
        }
        ds.setDefense(status);
        int updated = deviceStatusMapper.updateById(ds);
        result.put("code", updated > 0 ? 200 : 500);
        result.put("message", updated > 0 ? action + "成功" : action + "失败");
        return result;
    }
}
