package com.example.securityagent.controller;

import com.example.securityagent.entity.FaceRecord;
import com.example.securityagent.service.FaceService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Face recognition proxy controller.
 * All frontend → Spring Boot → Python microservice (localhost:5000).
 * Endpoint mapping based on REAL Python web_app.py routes.
 */
@RestController
@RequestMapping("/api/face")
@CrossOrigin(origins = "*")
public class FaceController {

    private final FaceService faceService;

    public FaceController(FaceService faceService) {
        this.faceService = faceService;
    }

    // ═══════════════════════════════════════════
    //  Status — Python: GET /api/face/status
    // ═══════════════════════════════════════════

    @GetMapping("/status")
    public Map<String, Object> status() {
        return faceService.getStatus();
    }

    // ═══════════════════════════════════════════
    //  Register — Python: POST /api/face/register  (personId + image)
    // ═══════════════════════════════════════════

    @PostMapping("/register")
    public Map<String, Object> register(
            @RequestParam(value = "personId", required = false) String personId,
            @RequestParam(value = "personName", required = false) String personName,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "personType", required = false) String personType,
            @RequestParam(value = "deviceCode", required = false) String deviceCode,
            @RequestParam(value = "userId", required = false) Long userId) {
        String effectiveName = personName != null ? personName : (name != null ? name : personId);
        MultipartFile effectiveImage = image != null ? image : imageFile;
        if (effectiveName == null || effectiveName.isBlank()) {
            return Map.of("code", 400, "message", "姓名不能为空");
        }
        return faceService.registerFace(effectiveName, effectiveImage, deviceCode, userId, personType);
    }

    // ═══════════════════════════════════════════
    //  Identify — Python: POST /api/face/identify  (multipart image + deviceCode/location/threshold)
    // ═══════════════════════════════════════════

    @PostMapping("/identify")
    public Map<String, Object> identify(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "deviceCode", required = false) String deviceCode,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "threshold", required = false) Double threshold,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "alarmMode", required = false, defaultValue = "stranger") String alarmMode) {
        return faceService.identifyFace(image, deviceCode, location, threshold, userId, alarmMode);
    }

    // ═══════════════════════════════════════════
    //  Record List — local DB
    // ═══════════════════════════════════════════

    @GetMapping("/record/list")
    public Map<String, Object> recordList(@RequestParam(required = false) Long userId) {
        List<FaceRecord> records = faceService.getRecordList(userId);
        return Map.of("code", 200, "message", "success", "data", records);
    }

    // ═══════════════════════════════════════════
    //  Live Detect — Python: POST /api/detect-live  (raw body dataURL, NOT multipart)
    //  Frontend sends dataURL string as request body (Content-Type: text/plain)
    // ═══════════════════════════════════════════

    @PostMapping("/live/detect")
    public Map<String, Object> liveDetect(@RequestBody String dataURL) {
        if (dataURL == null || dataURL.isEmpty()) {
            return Map.of("code", 400, "message", "缺少图片数据");
        }
        return faceService.detectLive(dataURL);
    }

    // ═══════════════════════════════════════════
    //  User Management — Python: /api/users/*
    // ═══════════════════════════════════════════

    @GetMapping("/users")
    public Map<String, Object> listUsers(@RequestParam(required = false) Long userId) {
        return faceService.listUsers(userId);
    }

    @PostMapping("/users/register")
    public Map<String, Object> registerUser(
            @RequestParam("name") String name,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        return faceService.registerUser(name, image);
    }

    @DeleteMapping("/users/{name}")
    public Map<String, Object> deleteUser(@PathVariable String name,
            @RequestParam(value = "userId", required = false) Long userId) {
        return faceService.deleteUser(name, userId);
    }

    @PostMapping("/users/register-batch")
    public Map<String, Object> registerBatch() {
        return faceService.registerBatch();
    }

    // ═══════════════════════════════════════════
    //  TTS — Python: POST /api/tts/synthesize  (JSON body)
    // ═══════════════════════════════════════════

    @PostMapping("/tts/synthesize")
    public Map<String, Object> synthesizeTts(@RequestBody Map<String, Object> request) {
        String text = (String) request.getOrDefault("text", "");
        double speed = request.get("speed") instanceof Number
                ? ((Number) request.get("speed")).doubleValue()
                : 1.0;
        return faceService.synthesizeTts(text, speed);
    }

    // ═══════════════════════════════════════════
    //  Sync Status — Python: GET /api/sync/status
    // ═══════════════════════════════════════════

    @GetMapping("/sync/status")
    public Map<String, Object> syncStatus() {
        return faceService.getSyncStatus();
    }
}
