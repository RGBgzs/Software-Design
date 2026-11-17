package com.example.securityagent.service;

import com.example.securityagent.dto.FaceResult;
import com.example.securityagent.entity.FacePerson;
import com.example.securityagent.entity.FaceRecord;
import com.example.securityagent.mapper.FacePersonMapper;
import com.example.securityagent.mapper.FaceRecordMapper;
import com.example.security.entity.AlarmRecord;
import com.example.security.mapper.AlarmRecordMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Face recognition business service.
 * Proxies Python microservice and persists records/alarms locally.
 */
@Service
public class FaceService {

    private static final Logger log = LoggerFactory.getLogger(FaceService.class);

    private final FaceRemoteClient client;
    private final FacePersonMapper facePersonMapper;
    private final FaceRecordMapper faceRecordMapper;
    private final AlarmRecordMapper alarmRecordMapper;

    @Value("${face.service.threshold:0.4}")
    private double defaultThreshold;

    public FaceService(FaceRemoteClient client,
                       FacePersonMapper facePersonMapper,
                       FaceRecordMapper faceRecordMapper,
                       AlarmRecordMapper alarmRecordMapper) {
        this.client = client;
        this.facePersonMapper = facePersonMapper;
        this.faceRecordMapper = faceRecordMapper;
        this.alarmRecordMapper = alarmRecordMapper;
    }

    // ═══════════════════════════════════════════
    //  Status — Python /api/face/status already {code, message, data}
    // ═══════════════════════════════════════════

    public Map<String, Object> getStatus() {
        JsonNode resp = client.getStatus();
        if (resp == null) {
            return Map.of("code", 1005, "message", "人脸识别服务不可用，请先启动 Python 服务", "available", false);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", true);
        result.put("code", resp.path("code").asInt(200));
        result.put("message", resp.path("message").asText("success"));
        result.put("data", resp.path("data"));
        return result;
    }

    // ═══════════════════════════════════════════
    //  Register — Python /api/face/register
    // ═══════════════════════════════════════════

    public Map<String, Object> registerFace(String personName, MultipartFile image, String deviceCode) {
        return registerFace(personName, image, deviceCode, null);
    }

    public Map<String, Object> registerFace(String personName, MultipartFile image, String deviceCode, Long userId) {
        return registerFace(personName, image, deviceCode, userId, null);
    }

    public Map<String, Object> registerFace(String personName, MultipartFile image, String deviceCode, Long userId, String personType) {
        if (personName == null || personName.isBlank()) {
            return Map.of("code", 400, "message", "姓名不能为空");
        }
        if (image == null || image.isEmpty()) {
            FacePerson fp = saveFacePersonToDb(personName, deviceCode, null, userId, personType);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 200);
            result.put("message", "人员信息已保存（未上传图片）");
            result.put("data", toPersonMap(fp));
            return result;
        }

        // 保存人脸图片到本地
        String savedImagePath = null;
        try {
            String uploadDir = "uploads/faces/" + (userId != null ? userId : "unknown");
            java.io.File dir = new java.io.File(uploadDir);
            if (!dir.exists()) dir.mkdirs();
            String fileName = personName + "_" + System.currentTimeMillis() + ".jpg";
            java.io.File dest = new java.io.File(dir, fileName);
            java.nio.file.Files.write(dest.toPath(), image.getBytes());
            savedImagePath = uploadDir + "/" + fileName;
            System.out.println("人脸图片已保存: " + savedImagePath);
        } catch (Exception e) {
            log.warn("保存人脸图片失败: {}", e.getMessage());
            e.printStackTrace();
        }

        JsonNode resp = client.registerFace(personName, image, deviceCode);
        if (resp == null) {
            FacePerson fp = saveFacePersonToDb(personName, deviceCode, savedImagePath, userId, personType);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 200);
            result.put("message", "人员已注册到本地数据库（人脸识别服务未连接）");
            result.put("data", toPersonMap(fp));
            return result;
        }
        int code = resp.path("code").asInt(500);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("message", resp.path("message").asText(code == 200 ? "注册成功" : "注册失败"));
        result.put("data", resp.path("data"));

        try {
            JsonNode data = resp.path("data");
            FacePerson fp = saveFacePersonToDb(personName, deviceCode,
                    savedImagePath != null ? savedImagePath : data.path("name").asText(null), userId, personType);
            result.put("localRecord", toPersonMap(fp));
        } catch (Exception e) {
            log.warn("Failed to save face_person locally: {}", e.getMessage());
            saveFacePersonToDb(personName, deviceCode, savedImagePath, userId, personType);
        }
        return result;
    }

    private FacePerson saveFacePersonToDb(String personName, String deviceCode, String faceImagePath) {
        return saveFacePersonToDb(personName, deviceCode, faceImagePath, null);
    }

    private FacePerson saveFacePersonToDb(String personName, String deviceCode, String faceImagePath, Long userId) {
        return saveFacePersonToDb(personName, deviceCode, faceImagePath, userId, null);
    }

    private FacePerson saveFacePersonToDb(String personName, String deviceCode, String faceImagePath, Long userId, String personType) {
        FacePerson fp = new FacePerson();
        fp.setPersonId(personName);
        fp.setName(personName);
        fp.setUserId(userId);
        fp.setPersonType(personType);
        fp.setDeviceCode(deviceCode != null ? deviceCode : "CAMERA_001");
        fp.setRegisterSource("MANUAL");
        fp.setStatus(1);
        fp.setImagePath(faceImagePath != null ? faceImagePath : ("face_db/" + personName + "/"));
        fp.setCreatedBy("web");
        facePersonMapper.insert(fp);
        return fp;
    }

    private Map<String, Object> toPersonMap(FacePerson fp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", fp.getId());
        m.put("name", fp.getName());
        m.put("personName", fp.getName());
        m.put("personId", fp.getPersonId());
        m.put("personType", fp.getPersonType() != null ? fp.getPersonType() : "家人");
        m.put("deviceCode", fp.getDeviceCode());
        m.put("imagePath", fp.getImagePath());
        m.put("faceCount", 1);
        m.put("createdAt", fp.getCreatedAt() != null ? fp.getCreatedAt().toString() : null);
        m.put("createTime", fp.getCreatedAt() != null ? fp.getCreatedAt().toString() : null);
        return m;
    }

    // ═══════════════════════════════════════════
    //  Identify — Python /api/face/identify returns {code, message, data}
    //  data = {matched, name, personType, similarity, deviceCode, location, riskLevel, suggestion, face_index}
    // ═══════════════════════════════════════════

    public Map<String, Object> identifyFace(MultipartFile image, String deviceCode, String location, Double threshold) {
        return identifyFace(image, deviceCode, location, threshold, null, "stranger");
    }

    public Map<String, Object> identifyFace(MultipartFile image, String deviceCode, String location, Double threshold, Long userId) {
        return identifyFace(image, deviceCode, location, threshold, userId, "stranger");
    }

    public Map<String, Object> identifyFace(MultipartFile image, String deviceCode, String location, Double threshold, Long userId, String alarmMode) {
        if (image == null || image.isEmpty()) {
            return Map.of("code", 400, "message", "请上传待识别的人脸图片");
        }
        if (threshold == null) threshold = defaultThreshold;
        if (deviceCode == null || deviceCode.isEmpty()) deviceCode = "CAMERA_001";
        if (location == null || location.isEmpty()) location = "未知位置";

        // CRITICAL: check if user has registered faces
        int registeredCount = 0;
        if (userId != null) { try { registeredCount = facePersonMapper.countByUserId(userId); } catch (Exception e) { log.warn("countByUserId: {}", e.getMessage()); } }
        if (userId != null && registeredCount == 0) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 200);
            result.put("message", "please register a face first");
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("recognized", false); d.put("matched", false); d.put("reason", "NO_REGISTERED_FACE");
            d.put("name", null); d.put("similarity", 0.0); d.put("registeredCount", 0);
            result.put("data", d);
            return result;
        }

        JsonNode resp = client.identifyFace(image, deviceCode, location, threshold, userId);
        if (resp == null) {
            return Map.of("code", 1005, "message", "人脸识别服务不可用，请先启动 Python 服务");
        }

        int code = resp.path("code").asInt(500);
        JsonNode data = resp.path("data");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("message", resp.path("message").asText(""));

        if (code == 200 && data != null && !data.isMissingNode()) {
            boolean matched = data.path("matched").asBoolean(false);
            String name = data.path("name").asText("陌生人");
            String personType = data.path("personType").asText("UNKNOWN");
            double similarity = data.path("similarity").asDouble(0.0);
            String riskLevel = data.path("riskLevel").asText("LOW");

            // ── 按用户过滤：只认当前用户注册的人脸 ──
            if (userId != null && matched) {
                List<FacePerson> myPersons = facePersonMapper.findByUserId(userId);
                Set<String> myNames = new java.util.HashSet<>();
                for (FacePerson p : myPersons) {
                    if (p.getStatus() != null && p.getStatus() == 1) {
                        myNames.add(p.getName());
                        myNames.add(p.getPersonId());
                    }
                }
                if (!myNames.contains(name)) {
                    matched = false;
                    personType = "UNKNOWN";
                    riskLevel = "MEDIUM";
                }
            }

            // ── 告警生成 ──
            boolean isStranger = !matched || "UNKNOWN".equalsIgnoreCase(personType) || "HIGH".equalsIgnoreCase(riskLevel);
            boolean isSpoof = "假体攻击".equals(name);
            boolean isPirMode = "all".equalsIgnoreCase(alarmMode);

            Long alarmId = null;
            if (isSpoof) {
                // 假体攻击：PIR模式→高危，监测模式→低危
                alarmId = createAlarm(deviceCode, location, "FACE_SPOOF",
                        "检测到疑似照片/视频人脸攻击",
                        isPirMode ? "HIGH" : "LOW");
            } else if (isStranger) {
                // 陌生人：始终高危
                alarmId = createAlarm(deviceCode, location, "FACE_UNKNOWN",
                        "检测到陌生人进入" + location, "HIGH");
            } else if (isPirMode && matched) {
                // 熟人：仅PIR模式生成低危告警
                alarmId = createAlarm(deviceCode, location, "FACE_KNOWN",
                        "已登记人员 " + name + " 经过", "LOW");
            }

            // ── 保存告警抓拍照片 ──
            if (alarmId != null && image != null && !image.isEmpty()) {
                try {
                    String imgDir = "uploads/alarms";
                    java.io.File dir = new java.io.File(imgDir);
                    if (!dir.exists()) dir.mkdirs();
                    String imgName = "alarm_" + alarmId + ".jpg";
                    java.io.File dest = new java.io.File(dir, imgName);
                    java.nio.file.Files.write(dest.toPath(), image.getBytes());
                    // 回写 image_path
                    AlarmRecord alarm = alarmRecordMapper.selectById(alarmId);
                    if (alarm != null) {
                        alarm.setImagePath(imgDir + "/" + imgName);
                        alarmRecordMapper.updateById(alarm);
                    }
                    System.out.println("告警抓拍已保存: " + imgDir + "/" + imgName);
                } catch (Exception e) {
                    log.warn("保存告警抓拍失败: {}", e.getMessage());
                }
            }

            // ── Save FaceRecord ──
            FaceRecord record = new FaceRecord();
            record.setUserId(userId);
            record.setPersonName(name);
            record.setPersonType(personType);
            record.setMatched(matched);
            record.setResultType(matched ? "KNOWN" : "UNKNOWN");
            record.setReason(matched ? "MATCHED" : "NO_MATCH");
            record.setSimilarity(similarity);
            record.setThresholdValue(threshold);
            record.setDeviceCode(deviceCode);
            record.setLocation(location);
            record.setRiskLevel(riskLevel);
            record.setSuggestion(data.path("suggestion").asText(""));
            record.setAlarmId(alarmId);
            record.setCaptureTime(LocalDateTime.now());
            try {
                faceRecordMapper.insert(record);
            } catch (Exception e) {
                log.warn("Failed to save face_record: {}", e.getMessage());
            }

            // ── Build response ──
            FaceResult fr = new FaceResult();
            fr.setMatched(matched);
            fr.setName(name);
            fr.setPersonType(personType);
            fr.setSimilarity(similarity);
            fr.setThreshold(threshold);
            fr.setDeviceCode(deviceCode);
            fr.setLocation(location);
            fr.setRiskLevel(riskLevel);
            fr.setSuggestion(data.path("suggestion").asText(""));
            fr.setRecordId(record.getId());
            fr.setAlarmId(alarmId);

            result.put("data", fr);
        } else {
            result.put("data", null);
        }
        return result;
    }

    // ═══════════════════════════════════════════
    //  Record List — local database
    // ═══════════════════════════════════════════

    public List<FaceRecord> getRecordList() {
        return faceRecordMapper.findAll();
    }

    public List<FaceRecord> getRecordList(Long userId) {
        if (userId != null) return faceRecordMapper.findByUserId(userId);
        return faceRecordMapper.findAll();
    }

    // ═══════════════════════════════════════════
    //  Live Detect — Python /api/detect-live (raw body dataURL)
    //  Returns {faces: [{bbox, name, similarity, matched, liveness, blocked}], ts, ms, tts_text}
    // ═══════════════════════════════════════════

    public Map<String, Object> detectLive(String dataURL) {
        // 1. Try native detect-live endpoint first
        JsonNode resp = client.detectLive(dataURL);
        if (resp != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            if (resp.has("ok")) {
                boolean ok = resp.path("ok").asBoolean(false);
                result.put("code", ok ? 200 : 400);
                result.put("message", ok ? "success" : resp.path("error").asText("detect failed"));
                result.put("data", resp.path("data"));

                // Save detection record to database
                saveLiveDetectRecord(resp.path("data"), "CAMERA_001");
            } else {
                result.put("code", 200);
                result.put("message", "success");
                result.put("data", resp);
                saveLiveDetectRecord(resp, "CAMERA_001");
            }
            return result;
        }

        // 2. Fallback: decode dataURL → call /api/face/identify
        try {
            byte[] imageBytes = decodeDataURL(dataURL);
            if (imageBytes == null || imageBytes.length == 0) {
                return Map.of("code", 400, "message", "无法解析图片数据");
            }
            // Call identify with raw bytes
            JsonNode identifyResp = client.identifyFaceFromBytes(
                imageBytes, "live.jpg", "CAMERA_001", "实时检测", defaultThreshold);
            if (identifyResp == null) {
                return Map.of("code", 1005, "message", "人脸识别服务不可用", "available", false);
            }

            // Convert identify response → detect-live format
            Map<String, Object> liveData = convertIdentifyToLiveFormat(identifyResp);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 200);
            result.put("message", "success (identify fallback)");
            result.put("data", liveData);

            // Save to database
            saveFallbackRecord(identifyResp, "CAMERA_001");
            return result;
        } catch (Exception e) {
            log.error("detectLive fallback failed: {}", e.getMessage());
            return Map.of("code", 1005, "message", "实时检测处理失败: " + e.getMessage(), "available", false);
        }
    }

    /** Save live detect record to face_record table */
    private void saveLiveDetectRecord(JsonNode data, String deviceCode) {
        try {
            JsonNode faces = data.path("faces");
            if (faces.isArray() && faces.size() > 0) {
                for (JsonNode face : faces) {
                    FaceRecord record = new FaceRecord();
                    record.setPersonName(face.path("name").asText("陌生人"));
                    record.setPersonType(face.path("matched").asBoolean(false) ? "REGISTERED" : "UNKNOWN");
                    record.setMatched(face.path("matched").asBoolean(false));
                    record.setSimilarity(face.path("similarity").asDouble(0.0));
                    record.setThresholdValue(defaultThreshold);
                    record.setDeviceCode(deviceCode);
                    record.setLocation("实时检测");
                    record.setRiskLevel(face.path("matched").asBoolean(false) ? "LOW" : "HIGH");
                    record.setCaptureTime(LocalDateTime.now());
                    faceRecordMapper.insert(record);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to save live detect record: {}", e.getMessage());
        }
    }

    /** Save fallback identify record */
    private void saveFallbackRecord(JsonNode identifyResp, String deviceCode) {
        try {
            JsonNode data = identifyResp.path("data");
            FaceRecord record = new FaceRecord();
            record.setPersonName(resolveRealName(data));
            record.setPersonType(data.path("personType").asText("UNKNOWN"));
            record.setMatched(data.path("matched").asBoolean(false));
            record.setSimilarity(data.path("similarity").asDouble(0.0));
            record.setThresholdValue(defaultThreshold);
            record.setDeviceCode(deviceCode);
            record.setLocation("实时检测(回退)");
            record.setRiskLevel(data.path("matched").asBoolean(false) ? "LOW" : "HIGH");
            record.setCaptureTime(LocalDateTime.now());
            faceRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("Failed to save fallback record: {}", e.getMessage());
        }
    }

    private String findFirstRegisteredName(Long userId) {
        try {
            List<FacePerson> persons = facePersonMapper.findAll();
            if (persons != null && !persons.isEmpty()) {
                for (FacePerson p : persons) {
                    String n = (p.getName() != null && !p.getName().isEmpty()) ? p.getName() : p.getPersonId();
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        } catch (Exception e) {
            log.warn("findFirstRegisteredName failed: {}", e.getMessage());
        }
        return null;
    }

    private String resolveRealName(JsonNode data) {
        // Try multiple candidate fields, skip generic/placeholder names
        String[] candidates = {
            data.path("personName").asText(null),
            data.path("realName").asText(null),
            data.path("name").asText(null),
            data.path("best_match").asText(null),
            data.path("bestMatch").asText(null),
            data.path("person").asText(null),
            data.path("personId").asText(null)
        };
        for (String candidate : candidates) {
            if (candidate == null) continue;
            String trimmed = candidate.trim();
            if (trimmed.isEmpty()) continue;
            String lower = trimmed.toLowerCase();
            if (lower.equals("unknown") || trimmed.equals("陌生人") || trimmed.equals("-")
                || trimmed.equals("undefined") || trimmed.equals("null")) continue;
            if (trimmed.equals("家庭成员") || trimmed.equals("已注册人员")
                || trimmed.equals("熟人") || lower.equals("family")) continue;
            return trimmed;
        }
        return "陌生人";
    }

    private byte[] decodeDataURL(String dataURL) {
        if (dataURL == null || dataURL.isEmpty()) return null;
        try {
            String b64 = dataURL;
            if (b64.contains(",")) {
                b64 = b64.substring(b64.indexOf(",") + 1);
            }
            return java.util.Base64.getDecoder().decode(b64);
        } catch (Exception e) {
            log.warn("decodeDataURL failed: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> convertIdentifyToLiveFormat(JsonNode identifyResp) {
        Map<String, Object> liveData = new LinkedHashMap<>();
        int code = identifyResp.path("code").asInt(200);
        JsonNode data = identifyResp.path("data");

        // Try multiple fields for real name, skip generic placeholders
        String name = resolveRealName(data);
        boolean matched = data.path("matched").asBoolean(false);
        double similarity = data.path("similarity").asDouble(0.0);
        String personType = data.path("personType").asText("UNKNOWN");

        // If Python returned a generic name but face IS matched, look up real name from local DB
        if (("陌生人".equals(name) || "已注册人员".equals(name)) && matched) {
            String localName = findFirstRegisteredName(null);
            if (localName != null && !localName.isEmpty()) {
                name = localName;
            }
        }

        Map<String, Object> face = new LinkedHashMap<>();
        face.put("name", name);
        face.put("personName", name);
        face.put("matched", matched);
        face.put("similarity", similarity);
        face.put("personType", personType);
        face.put("blocked", "假体攻击".equals(name));
        face.put("liveness", Map.of("is_real", !"假体攻击".equals(name)));
        face.put("bbox", java.util.Collections.emptyList());

        liveData.put("faces", java.util.Collections.singletonList(face));
        liveData.put("ms", 0);
        return liveData;
    }

    // ═══════════════════════════════════════════
    //  User Management — Python /api/users/*
    //  Native format: {ok, data: {users: [...], total: N}}
    // ═══════════════════════════════════════════

    public Map<String, Object> listUsers() {
        return listUsers(null);
    }

    public Map<String, Object> listUsers(Long userId) {
        List<FacePerson> persons;
        if (userId != null) { persons = facePersonMapper.findByUserId(userId); }
        else { persons = facePersonMapper.findAll(); }
        List<Map<String, Object>> users = new ArrayList<>();
        for (FacePerson p : persons) {
            if (p.getStatus() == null || p.getStatus() != 1) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("name", p.getName() != null ? p.getName() : p.getPersonId());
            item.put("personName", p.getName() != null ? p.getName() : p.getPersonId());
            item.put("personId", p.getPersonId());
            item.put("personType", "居民");
            item.put("type", "居民");
            item.put("deviceCode", p.getDeviceCode());
            item.put("imagePath", p.getImagePath());
            item.put("faceImage", p.getImagePath());
            item.put("faceCount", 1);
            item.put("face_count", 1);
            item.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
            item.put("createTime", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
            item.put("registeredAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
            item.put("status", p.getStatus());
            users.add(item);
        }

        // Try to merge Python data if available (only when no userId filter)
        if (userId == null) {
            try {
                JsonNode resp = client.listUsers();
            if (resp != null) {
                JsonNode data = resp.path("data");
                JsonNode pyUsers = data.path("users");
                if (!pyUsers.isMissingNode() && pyUsers.isArray()) {
                    for (JsonNode pyUser : pyUsers) {
                        String pyName = pyUser.path("name").asText(null);
                        if (pyName == null || pyName.isEmpty()) continue;
                        // Check if already in local list
                        boolean found = false;
                        for (Map<String, Object> local : users) {
                            if (pyName.equals(local.get("name"))) {
                                found = true;
                                // Merge Python data
                                local.put("faceCount", Math.max(
                                    (Integer) local.getOrDefault("faceCount", 1),
                                    pyUser.path("face_count").asInt(1)));
                                break;
                            }
                        }
                        if (!found) {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("id", null);
                            item.put("name", pyName);
                            item.put("personName", pyName);
            item.put("personType", "家人");
                            item.put("faceCount", pyUser.path("face_count").asInt(1));
                            item.put("createdAt", null);
                            users.add(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to merge Python user data: {}", e.getMessage());
        }
        } // end if (userId == null)

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", users);
        result.put("total", users.size());
        return result;
    }

    private Map<String, Object> listUsersFromLocalDb() {
        List<FacePerson> persons = facePersonMapper.findAll();
        // Group by name and count faces per person
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (FacePerson p : persons) {
            String name = (p.getName() != null && !p.getName().isEmpty()) ? p.getName() : p.getPersonId();
            if (name == null || name.isEmpty()) continue;
            grouped.computeIfAbsent(name, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", name);
                m.put("face_count", 0);
                return m;
            });
            Map<String, Object> entry = grouped.get(name);
            entry.put("face_count", (Integer) entry.get("face_count") + 1);
        }
        List<Map<String, Object>> users = new ArrayList<>(grouped.values());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "success (local db)");
        result.put("data", users);
        result.put("total", users.size());
        return result;
    }

    public Map<String, Object> registerUser(String name, MultipartFile image) {
        JsonNode resp = client.registerUser(name, image);
        if (resp == null) {
            return Map.of("code", 1005, "message", "用户管理接口暂未接入，当前仅支持基础人脸注册");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", resp.path("ok").asBoolean(false) ? 200 : 400);
        result.put("message", resp.path("ok").asBoolean(false)
                ? resp.path("data").path("message").asText("注册成功")
                : resp.path("error").asText("注册失败"));
        result.put("data", resp.path("data"));
        return result;
    }

    public Map<String, Object> deleteUser(String name) {
        return deleteUser(name, null);
    }

    public Map<String, Object> deleteUser(String name, Long userId) {
        try { client.deleteUser(name); } catch (Exception e) { log.warn("Python delete failed: {}", e.getMessage()); }

        try {
            List<FacePerson> persons = facePersonMapper.findAll();
            for (FacePerson p : persons) {
                boolean nameMatch = name.equals(p.getName()) || name.equals(p.getPersonId());
                boolean userMatch = userId == null || userId.equals(p.getUserId());
                if (nameMatch && userMatch) {
                    if (p.getImagePath() != null && !p.getImagePath().isEmpty()) {
                        try {
                            java.io.File imgFile = new java.io.File(p.getImagePath());
                            if (imgFile.exists()) imgFile.delete();
                        } catch (Exception e) { log.warn("删除照片文件失败: {}", e.getMessage()); }
                    }
                }
            }
            if (userId != null) {
                facePersonMapper.deleteByNameAndUserId(name, userId);
            } else {
                facePersonMapper.deleteByName(name);
            }
            return Map.of("code", 200, "message", "用户 " + name + " 已删除");
        } catch (Exception e) {
            log.warn("deleteUser failed: {}", e.getMessage());
            return Map.of("code", 500, "message", "删除失败");
        }
    }

    public Map<String, Object> registerBatch() {
        JsonNode resp = client.registerBatch();
        if (resp == null) {
            return Map.of("code", 1005, "message", "用户管理接口暂未接入");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", resp.path("ok").asBoolean(false) ? 200 : 400);
        result.put("message", resp.path("data").path("message").asText("批量注册完成"));
        result.put("data", resp.path("data"));
        return result;
    }

    // ═══════════════════════════════════════════
    //  TTS — Python /api/tts/synthesize (JSON: {text, speed, sid})
    //  Returns {ok, data: {audio_base64, sample_rate, duration, text, speed, format}}
    // ═══════════════════════════════════════════

    public Map<String, Object> synthesizeTts(String text, double speed) {
        JsonNode resp = client.synthesizeTts(text, speed, 0);
        if (resp == null) {
            return Map.of("code", 1005, "message", "语音播报接口暂未接入", "available", false);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        boolean ok = resp.path("ok").asBoolean(false);
        result.put("code", ok ? 200 : 400);
        result.put("message", ok ? "合成成功" : resp.path("error").asText("合成失败"));
        result.put("data", resp.path("data"));
        return result;
    }

    // ═══════════════════════════════════════════
    //  Sync Status — Python /api/sync/status
    // ═══════════════════════════════════════════

    public Map<String, Object> getSyncStatus() {
        JsonNode resp = client.getSyncStatus();
        if (resp == null) {
            return Map.of("code", 1005, "message", "同步状态暂未接入");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", resp.path("ok").asBoolean(false) ? 200 : 400);
        result.put("message", "success");
        result.put("data", resp.path("data"));
        return result;
    }

    // ═══════════════════════════════════════════
    //  Private: alarm generation
    // ═══════════════════════════════════════════

    private Long createAlarm(String deviceCode, String location, String alarmType, String alarmContent) {
        return createAlarm(deviceCode, location, alarmType, alarmContent, "HIGH");
    }

    private Long createAlarm(String deviceCode, String location, String alarmType, String alarmContent, String alarmLevel) {
        try {
            AlarmRecord alarm = new AlarmRecord();
            alarm.setDeviceCode(deviceCode);
            alarm.setDeviceName("人脸识别摄像头");
            alarm.setLocation(location);
            alarm.setAlarmType(alarmType);
            alarm.setAlarmLevel(alarmLevel != null ? alarmLevel : "HIGH");
            alarm.setAlarmContent(alarmContent + " [" + LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")) + "]");
            alarm.setHandleStatus(0);
            alarm.setAlarmTime(LocalDateTime.now());
            alarmRecordMapper.insert(alarm);
            log.info("Generated alarm: id={}, type={}, level={}, location={}", alarm.getId(), alarmType, alarmLevel, location);
            return alarm.getId();
        } catch (Exception e) {
            log.error("Failed to create alarm: {}", e.getMessage());
            return null;
        }
    }
}
