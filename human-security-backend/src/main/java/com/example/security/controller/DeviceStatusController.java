package com.example.security.controller;

import com.example.security.entity.DeviceStatus;
import com.example.security.service.DeviceStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device")
public class DeviceStatusController {

    @Autowired
    private DeviceStatusService deviceStatusService;

    /**
     * GET /api/device/status
     */
    @GetMapping("/status")
    public List<DeviceStatus> status() {
        return deviceStatusService.getAll();
    }
}
