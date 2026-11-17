package com.example.security.controller;

import com.example.security.entity.AlarmRecord;
import com.example.security.service.AlarmRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/alarm")
public class AlarmRecordController {

    @Autowired
    private AlarmRecordService alarmRecordService;

    /**
     * GET /api/alarm/list
     * 可选参数：status, level
     */
    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String level) {
        List<AlarmRecord> list = alarmRecordService.list(status, level);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("total", list.size());
        result.put("data", list);
        // 附加摘要文本
        result.put("summary", alarmRecordService.buildAllAlarmSummary(list));
        return result;
    }

    /**
     * GET /api/alarm/latest
     * 返回最新一条未处理告警
     */
    @GetMapping("/latest")
    public Map<String, Object> latest() {
        AlarmRecord alarm = alarmRecordService.getLatestUnhandled();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        if (alarm != null) {
            result.put("data", alarm);
            result.put("hasAlarm", true);
            result.put("message", "存在未处理告警");
            result.put("speechText", alarmRecordService.buildAlarmSpeechText(alarm));
        } else {
            result.put("data", null);
            result.put("hasAlarm", false);
            result.put("message", "当前暂无未处理告警");
            result.put("speechText", "");
        }
        return result;
    }

    /**
     * POST /api/alarm/{id}/handle
     * 将指定告警标记为已处理
     */
    @PostMapping("/{id}/handle")
    public Map<String, Object> handle(@PathVariable Long id) {
        boolean success = alarmRecordService.handle(id);

        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "告警已处理");
        } else {
            result.put("code", 404);
            result.put("message", "告警不存在或处理失败");
        }
        return result;
    }
}
