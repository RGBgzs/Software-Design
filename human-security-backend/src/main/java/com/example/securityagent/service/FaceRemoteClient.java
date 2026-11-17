package com.example.securityagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * HTTP client for Python face recognition microservice (localhost:5000).
 * All methods call REAL endpoints verified in web_app.py.
 */
@Component
public class FaceRemoteClient {

    private static final Logger log = LoggerFactory.getLogger(FaceRemoteClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${face.service.base-url:http://localhost:5000}")
    private String baseUrl;

    @Value("${face.service.enabled:true}")
    private boolean enabled;

    @Value("${face.service.read-timeout:30000}")
    private int readTimeout;

    @Value("${face.service.connect-timeout:5000}")
    private int connectTimeout;

    public FaceRemoteClient() {
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        if (restTemplate.getRequestFactory() instanceof org.springframework.http.client.SimpleClientHttpRequestFactory) {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                    (org.springframework.http.client.SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
            factory.setConnectTimeout(connectTimeout);
            factory.setReadTimeout(readTimeout);
        }
    }

    // ═══════════════════════════════════════════
    //  Status — Python: GET /api/face/status  (Spring Boot compatible)
    // ═══════════════════════════════════════════

    public JsonNode getStatus() {
        if (!enabled) return buildDisabled();
        return getJson("/api/face/status");
    }

    // ═══════════════════════════════════════════
    //  Register — Python: POST /api/face/register  (personId + image multipart)
    // ═══════════════════════════════════════════

    public JsonNode registerFace(String personId, MultipartFile image, String deviceCode) {
        return registerFace(personId, image, deviceCode, null);
    }

    public JsonNode registerFace(String personId, MultipartFile image, String deviceCode, Long userId) {
        if (!enabled) return buildDisabled();
        try {
            String url = baseUrl + "/api/face/register";
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("personId", personId != null ? personId : "");
            body.add("image", new ByteArrayResource(image.getBytes()) {
                @Override public String getFilename() {
                    return image.getOriginalFilename() != null ? image.getOriginalFilename() : "face.jpg";
                }
            });
            if (deviceCode != null && !deviceCode.isEmpty()) body.add("deviceCode", deviceCode);
            if (userId != null) body.add("userId", String.valueOf(userId));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            return postJson(url, new HttpEntity<>(body, headers));
        } catch (Exception e) {
            log.error("registerFace failed: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════
    //  Identify with raw bytes — used as fallback for live detect
    // ═══════════════════════════════════════════

    public JsonNode identifyFaceFromBytes(byte[] imageBytes, String filename,
                                          String deviceCode, String location, Double threshold) {
        if (!enabled) return buildDisabled();
        try {
            String url = baseUrl + "/api/face/identify";
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", new ByteArrayResource(imageBytes) {
                @Override public String getFilename() {
                    return filename != null ? filename : "live.jpg";
                }
            });
            if (deviceCode != null && !deviceCode.isEmpty()) body.add("deviceCode", deviceCode);
            if (location != null && !location.isEmpty()) body.add("location", location);
            if (threshold != null) body.add("threshold", String.valueOf(threshold));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            return postJson(url, new HttpEntity<>(body, headers));
        } catch (Exception e) {
            log.error("identifyFaceFromBytes failed: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════
    //  Identify — Python: POST /api/face/identify  (multipart, returns {code, message, data})
    // ═══════════════════════════════════════════

    public JsonNode identifyFace(MultipartFile image, String deviceCode, String location, Double threshold) {
        return identifyFace(image, deviceCode, location, threshold, null);
    }

    public JsonNode identifyFace(MultipartFile image, String deviceCode, String location, Double threshold, Long userId) {
        if (!enabled) return buildDisabled();
        try {
            String url = baseUrl + "/api/face/identify";
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", new ByteArrayResource(image.getBytes()) {
                @Override public String getFilename() {
                    return image.getOriginalFilename() != null ? image.getOriginalFilename() : "face.jpg";
                }
            });
            if (deviceCode != null && !deviceCode.isEmpty()) body.add("deviceCode", deviceCode);
            if (location != null && !location.isEmpty()) body.add("location", location);
            if (threshold != null) body.add("threshold", String.valueOf(threshold));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            return postJson(url, new HttpEntity<>(body, headers));
        } catch (Exception e) {
            log.error("identifyFace failed: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════
    //  Live Detect — Python: POST /api/detect-live  (RAW BODY dataURL, NOT multipart!)
    //  web_app.py line 621: request.get_data(as_text=True)
    // ═══════════════════════════════════════════

    public JsonNode detectLive(String dataURL) {
        if (!enabled) return buildDisabled();
        try {
            String url = baseUrl + "/api/detect-live";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            HttpEntity<String> entity = new HttpEntity<>(dataURL, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return objectMapper.readTree(resp.getBody());
            }
        } catch (Exception e) {
            log.error("detectLive failed: {}", e.getMessage());
        }
        return null;
    }

    // ═══════════════════════════════════════════
    //  User Management — Python native /api/users/*
    //  Returns {ok, data: {users: [...], total: N}} or {ok, data: {message, name, ...}}
    // ═══════════════════════════════════════════

    public JsonNode listUsers() {
        if (!enabled) return buildDisabled();
        return getJson("/api/users");
    }

    public JsonNode registerUser(String name, MultipartFile image) {
        if (!enabled) return buildDisabled();
        try {
            String url = baseUrl + "/api/users/register";
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("name", name != null ? name : "");
            if (image != null && !image.isEmpty()) {
                body.add("image", new ByteArrayResource(image.getBytes()) {
                    @Override public String getFilename() {
                        return image.getOriginalFilename() != null ? image.getOriginalFilename() : "user.jpg";
                    }
                });
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            return postJson(url, new HttpEntity<>(body, headers));
        } catch (Exception e) {
            log.error("registerUser failed: {}", e.getMessage());
            return null;
        }
    }

    public boolean deleteUser(String name) {
        if (!enabled) return false;
        try {
            restTemplate.delete(baseUrl + "/api/users/" + name);
            return true;
        } catch (Exception e) {
            log.error("deleteUser failed: {}", e.getMessage());
            return false;
        }
    }

    public JsonNode registerBatch() {
        if (!enabled) return buildDisabled();
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    baseUrl + "/api/users/register-batch", null, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return objectMapper.readTree(resp.getBody());
            }
        } catch (Exception e) {
            log.error("registerBatch failed: {}", e.getMessage());
        }
        return null;
    }

    // ═══════════════════════════════════════════
    //  TTS — Python: POST /api/tts/synthesize  (JSON body: {text, speed, sid})
    //  Returns {ok, data: {audio_base64, sample_rate, duration, text, speed, format}}
    // ═══════════════════════════════════════════

    public JsonNode synthesizeTts(String text, double speed, int sid) {
        if (!enabled) return buildDisabled();
        try {
            String url = baseUrl + "/api/tts/synthesize";
            Map<String, Object> reqBody = Map.of("text", text, "speed", speed, "sid", sid);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return postJson(url, new HttpEntity<>(reqBody, headers));
        } catch (Exception e) {
            log.error("synthesizeTts failed: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════
    //  Sync Status — Python: GET /api/sync/status
    // ═══════════════════════════════════════════

    public JsonNode getSyncStatus() {
        if (!enabled) return buildDisabled();
        return getJson("/api/sync/status");
    }

    // ═══════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════

    private JsonNode getJson(String path) {
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(baseUrl + path, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return objectMapper.readTree(resp.getBody());
            }
        } catch (Exception e) {
            log.error("GET {} failed: {}", path, e.getMessage());
        }
        return null;
    }

    private JsonNode postJson(String url, HttpEntity<?> entity) {
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return objectMapper.readTree(resp.getBody());
            }
        } catch (Exception e) {
            log.error("POST {} failed: {}", url, e.getMessage());
        }
        return null;
    }

    private JsonNode buildDisabled() {
        try {
            return objectMapper.readTree("{\"code\":1005,\"message\":\"人脸识别服务未启用\",\"data\":{}}");
        } catch (Exception e) {
            return null;
        }
    }
}
