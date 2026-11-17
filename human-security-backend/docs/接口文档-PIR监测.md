# PIR 人体感应监测接口文档

> 基础路径：`/api/pir`  
> 响应格式：JSON `{"code": 200, "data": ..., "message": "..."}`

---

## 1. GET /api/pir/status

获取所有 PIR 设备实时状态。

**请求参数**：无

**响应示例**：
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "deviceCode": "human_001",
      "deviceName": "客厅人体传感器",
      "location": "客厅",
      "onlineStatus": 1,
      "humanDetected": 0,
      "defenseStatus": 1,
      "lastReportTime": "2026-07-09 16:40:00",
      "lastOnline": "2026-07-09 16:40:00"
    }
  ],
  "total": 1
}
```

---

## 2. GET /api/pir/records

获取 PIR 人体感应记录（分页）。

**请求参数**：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码 |
| pageSize | int | 20 | 每页条数 |
| deviceCode | string | 可选 | 设备编号筛选 |
| startTime | string | 可选 | 开始时间 |
| endTime | string | 可选 | 结束时间 |

**响应示例**：
```json
{
  "code": 200,
  "data": [
    {
      "id": 100,
      "deviceCode": "human_001",
      "deviceName": "客厅人体传感器",
      "location": "客厅",
      "humanDetected": 1,
      "defenseStatus": 1,
      "isAlarm": 1,
      "alarmId": 25,
      "reportTime": "2026-07-09T16:40:00"
    }
  ],
  "total": 150,
  "page": 1,
  "pageSize": 20
}
```

---

## 3. POST /api/pir/report

手动上报 PIR 数据（HTTP 接口，用于模拟或第三方集成）。

**请求体**：
```json
{
  "deviceCode": "human_001",
  "humanDetected": 1,
  "defenseStatus": 1
}
```

**说明**：
- `humanDetected`: 0=无人，1=有人
- `defenseStatus`: 0=撤防，1=布防
- 当 `humanDetected=1` 且 `defenseStatus=1` 时，自动生成 `PIR_HUMAN_DETECTED` 告警
- 5分钟内同一设备不重复生成告警

**响应示例**：
```json
{
  "code": 200,
  "message": "上报成功"
}
```

---

## 4. GET /api/pir/stats

PIR 统计数据概览。

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "pirDeviceTotal": 5,
    "pirDeviceOnline": 3,
    "pirDeviceArmed": 2,
    "todayDetectionCount": 12,
    "todayAlarmCount": 2,
    "latestDetection": {
      "deviceCode": "human_001",
      "humanDetected": 1,
      "defenseStatus": 1,
      "reportTime": "2026-07-09 16:40:00",
      "isAlarm": 1
    }
  }
}
```

---

## 5. POST /api/pir/arm

对指定设备开启布防。

**请求体**：
```json
{
  "deviceCode": "human_001",
  "operator": "admin"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "布防指令已下发",
  "deviceCode": "human_001"
}
```

---

## 6. POST /api/pir/disarm

对指定设备撤销布防。

**请求体**：
```json
{
  "deviceCode": "human_001",
  "operator": "admin"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "撤防指令已下发",
  "deviceCode": "human_001"
}
```

---

## 7. GET /api/pir/today-count

今日人体感应次数。

**响应示例**：
```json
{
  "code": 200,
  "data": 12
}
```

---

## 8. GET /api/pir/latest

最近一次感应时间。

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "deviceCode": "human_001",
    "humanDetected": 1,
    "defenseStatus": 1,
    "reportTime": "2026-07-09 16:40:00",
    "isAlarm": 1
  }
}
```

---

## PIR 告警联动

### 生成条件
- `humanDetected == 1`（检测到人体）
- `defenseStatus == 1`（布防状态）
- 同一设备 5 分钟内不重复

### 告警类型
- `PIR_HUMAN_DETECTED` - PIR 人体感应告警
- 等级：HIGH
- 内容："{设备名} 在布防状态下检测到人体活动"

### MQTT 触发
通过 MQTT Topic 自动触发：
- `human/device/+/data` - 人体感应数据
- `pir/device/+/data` - PIR 设备数据
- `pir/device/+/alarm` - PIR 告警

---

## AI 助手 PIR 查询

支持的语音指令：
- "小安小安，查看人体感应状态"
- "小安小安，今天有没有人经过"
- "小安小安，打开 PIR 监测"
- "小安小安，分析最近一次人体感应告警"

AI 意图类型：`PIR_QUERY`
