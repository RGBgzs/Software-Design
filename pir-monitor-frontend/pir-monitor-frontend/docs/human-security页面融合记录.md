# human-security 页面融合记录

> 生成时间：2026-07-10
> 操作：将 `human-security-frontend` 页面融合到 `pir-monitor-frontend`

---

## 1. 从 human-security-frontend 迁移了哪些页面

| 源文件 | 目标文件 | 改造说明 |
|---|---|---|
| `HomeView.vue` | `PirMonitor.vue` | JS → TS, 接入 Pinia store, 使用 /api 相对路径 |
| `DashboardView.vue` | `Dashboard.vue` | JS → TS, 适配 StatisticsOverview 类型, 增加回退加载 |
| `DeviceView.vue` | `DeviceManage.vue` | JS → TS, 接入 @/api/humanSecurity, 增加搜索功能 |
| `AlarmView.vue` | `AlarmManage.vue` | fetch() → axios, 增加告警级别/状态筛选, 批量处理 |
| `RecordView.vue` | (合并到 PirMonitor.vue) | 历史记录表格合并到 PIR 监测页 |
| `ScreenView.vue` | `ScreenView.vue` | JS → TS, 移除全局样式泄露(scoped), 适配 Alarm 类型 |
| `LoginView.vue` | 不迁移 | 目标项目有独立登录系统 |
| `RegisterView.vue` | 不迁移 | 目标项目有独立注册系统 |
| `MainLayout.vue` | 不迁移 | 目标项目有 4 套角色布局 |

## 2. 新增了哪些 view

| 文件 | 说明 |
|---|---|
| `src/views/human-security/Index.vue` | 功能卡片入口页，8 个功能卡片 + 实时时钟 + MQTT 状态 |
| `src/views/human-security/Dashboard.vue` | 首页总控台 (改造) |
| `src/views/human-security/AiAssistant.vue` | AI 智能助手 (新建，含语音输入) |
| `src/views/human-security/DeviceManage.vue` | 设备管理 (改造) |
| `src/views/human-security/PirMonitor.vue` | PIR 人体感应监测 (改造) |
| `src/views/human-security/AlarmManage.vue` | 告警管理 (改造) |
| `src/views/human-security/FaceRecognition.vue` | 人脸识别 (新建) |
| `src/views/human-security/MessageCenter.vue` | 消息中心 (新建) |
| `src/views/human-security/EmergencyHelp.vue` | 紧急求助 (新建，含 SOS 一键求助) |
| `src/views/human-security/ScreenView.vue` | 全屏监控大屏 (改造) |

## 3. 新增了哪些 route

| 路径 | 组件 | 说明 |
|---|---|---|
| `/human-security` | `Index.vue` | 智慧安防控制台入口 |
| `/human-security/dashboard` | `Dashboard.vue` | 首页总控台 |
| `/human-security/ai` | `AiAssistant.vue` | AI 智能助手 |
| `/human-security/device` | `DeviceManage.vue` | 设备管理 |
| `/human-security/pir` | `PirMonitor.vue` | PIR 人体感应监测 |
| `/human-security/alarm` | `AlarmManage.vue` | 告警管理 |
| `/human-security/face` | `FaceRecognition.vue` | 人脸识别 |
| `/human-security/message` | `MessageCenter.vue` | 消息中心 |
| `/human-security/help` | `EmergencyHelp.vue` | 紧急求助 |
| `/human-security/screen` | `ScreenView.vue` | 全屏监控大屏 |

所有路由均设置 `meta: { auth: true }`，需登录后访问。

## 4. 新增了哪些菜单按钮

在 4 个布局中均新增了菜单项：

| 布局 | 菜单位置 |
|---|---|
| `AdminLayout.vue` | 侧边栏 "智慧安防控制台" (Platform 图标) |
| `MonitorLayout.vue` | 侧边栏 "智慧安防控制台" (Platform 图标) |
| `ResidentLayout.vue` | 侧边栏 "智慧安防控制台" (Platform 图标) |
| `GuardLayout.vue` | 侧边栏 "智慧安防控制台" (Platform 图标) |

## 5. 新增了哪些 api 文件

| 文件 | 说明 |
|---|---|
| `src/api/humanSecurity.ts` | 聚合 API 适配层，内部复用目标项目已有 API 模块 |

包含的 API：AI 对话、PIR 设备状态、人体感应记录、布防/撤防、设备 CRUD、告警管理、人脸注册/识别、统计数据、消息中心、紧急求助。

## 6. 哪些功能已接入

| 功能 | 状态 | 接口 |
|---|---|---|
| AI 智能对话 | ✅ 已接入 | `POST /ai/agent-chat` |
| PIR 设备状态 | ✅ 已接入 | `GET /devices/:code/status` |
| 布防/撤防 | ✅ 已接入 | `POST /devices/:code/defense/on\|off` |
| 人体感应记录 | ✅ 已接入 | `GET /records` |
| 设备管理 CRUD | ✅ 已接入 | `GET/POST/PUT/DELETE /devices` |
| 告警管理 | ✅ 已接入 | `GET /alarms`, `PUT /alarms/:id/handle` |
| 人脸注册/识别 | ✅ 已接入 | `POST /face/register`, `POST /face/identify` |
| 统计数据 | ✅ 已接入 | `GET /statistics/overview`, `GET /statistics/alarm-trend` |
| 语音助手 | ⚠️ Web Speech API (前端) | 浏览器原生语音识别 |
| 消息中心 | ⚠️ 预留接口 + 模拟数据 | `GET /messages`, `PUT /messages/:id/read` |
| 紧急求助 | ⚠️ 预留接口 + 离线模式 | `POST /emergency/help` |

## 7. 哪些功能暂未接入

| 功能 | 说明 |
|---|---|
| 语音命令后端处理 | 前端语音识别已实现，后端 `/ai/voice-command` 需实现 |
| 消息推送/WebSocket | 当前使用 HTTP 轮询 |
| 视频流对接 | 大屏页暂未接入摄像头 |
| MQTT 实时数据 | 各页面使用 5s HTTP 轮询，可升级为 MQTT 订阅 |
| 多设备切换 | PIR 监测页当前固定 `human_001`，可添加设备选择器 |

## 8. 构建是否通过

✅ **`npm run build` 通过**

- `vue-tsc -b` — TypeScript 类型检查通过，零错误
- `vite build` — 生产构建成功，903ms
- 输出目录：`dist/` (所有资源正常生成)

构建日志中的 warning：
- `[INVALID_ANNOTATION]` 来自 `@vueuse/core` 第三方库，非本项目问题
- chunk 大小警告来自 ECharts/Element Plus，非本项目问题

## 9. 修改的文件清单

| 文件 | 操作 |
|---|---|
| `src/router/index.ts` | 修改：新增 10 条 lazy-load 路由 |
| `src/layouts/AdminLayout.vue` | 修改：新增菜单项 + Platform 图标导入 |
| `src/layouts/MonitorLayout.vue` | 修改：新增菜单项 + Platform 图标导入 |
| `src/layouts/ResidentLayout.vue` | 修改：新增菜单项 + Platform 图标导入 |
| `src/layouts/GuardLayout.vue` | 修改：新增菜单项 + Platform 图标导入 |
| `src/stores/auth.ts` | 修复：data 类型标注 (预存 bug) |
| `src/composables/useFaceCapture.ts` | 修复：类型断言 (预存 bug) |
| `src/views/monitor/MonitorHome.vue` | 修复：移除未使用的 Device 导入 (预存 bug) |

## 10. 未修改的文件

以下文件**未修改**，保证原有功能完整：

- `src/main.ts` — 未修改
- `src/App.vue` — 未修改
- `src/style.css` — 未修改
- `src/stores/device.ts` — 未修改
- `src/stores/mqtt.ts` — 未修改
- `src/stores/alarm.ts` — 未修改
- `src/stores/chat.ts` — 未修改
- `src/api/index.ts` — 未修改
- `src/api/device.ts` — 未修改
- `src/api/alarm.ts` — 未修改
- `src/api/activity.ts` — 未修改
- `src/api/chat.ts` — 未修改
- `src/api/face.ts` — 未修改
- `src/api/auth.ts` — 未修改
- `src/api/camera.ts` — 未修改
- `src/api/statistics.ts` — 未修改
- `src/views/login/` — 未修改
- `src/views/resident/` — 未修改
- `src/views/guard/` — 未修改
- `src/views/admin/` — 未修改
- `src/views/monitor/` — 仅修复 1 处预存 bug
- `src/views/common/` — 未修改
- `src/components/` — 未修改
- `src/types/` — 未修改
- `src/composables/` — 仅修复 1 处预存 bug
- `index.html` — 未修改
- `vite.config.ts` — 未修改
- `package.json` — 未修改
