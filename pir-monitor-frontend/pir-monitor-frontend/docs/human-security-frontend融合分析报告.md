# human-security-frontend 融合分析报告

> 生成时间：2026-07-10

## 1. 源项目技术栈

| 项目 | human-security-frontend |
|---|---|
| **路径** | `E:\develop\claude\human-security-frontend` |
| **框架** | Vue 3.5 (Composition API, `<script setup>`) |
| **语言** | JavaScript (无 TypeScript) |
| **构建工具** | Vite 8 |
| **UI 库** | Element Plus 2.14 |
| **图表** | ECharts 6 (直接 `echarts.init()`, 无 vue-echarts) |
| **路由** | Vue Router 4 (Hash 模式 `createWebHashHistory`) |
| **HTTP** | Axios 1.18 (baseURL: `/api`, proxy → `localhost:8089`) |
| **状态管理** | 无 (使用 localStorage 存储 token) |
| **认证** | Token-based, localStorage, 路由守卫 |

## 2. 目标项目技术栈

| 项目 | pir-monitor-frontend |
|---|---|
| **路径** | `C:\Users\27822\Desktop\pir-monitor-frontend\pir-monitor-frontend` |
| **框架** | Vue 3.5 (Composition API, `<script setup lang="ts">`) |
| **语言** | TypeScript 6.0 |
| **构建工具** | Vite 8 |
| **UI 库** | Element Plus 2.14 + @element-plus/icons-vue |
| **图表** | ECharts 6 + vue-echarts 8 |
| **路由** | Vue Router 4 (History 模式 `createWebHistory`) |
| **HTTP** | Axios 1.18 (baseURL: `/api`, proxy → `localhost:8080`) |
| **状态管理** | Pinia 3.0 (auth, device, mqtt, alarm, chat) |
| **实时通信** | MQTT 5.15 (WebSocket) |
| **认证** | Token-based, Pinia store, 离线 mock 模式 |

## 3. 源项目有哪些页面

| 视图文件 | 功能 | 行数 |
|---|---|---|
| `LoginView.vue` | 登录页 (玻璃拟态卡片) | 141 |
| `RegisterView.vue` | 注册页 | 172 |
| `HomeView.vue` | 设备监控主页 (状态卡片 + 布防/撤防) | 265 |
| `DashboardView.vue` | 数据统计大屏 (ECharts 图表) | 183 |
| `DeviceView.vue` | 设备 CRUD 管理 | 155 |
| `AlarmView.vue` | 告警记录列表 | 105 |
| `RecordView.vue` | 人体感应历史记录 | 65 |
| `ScreenView.vue` | 全屏监控大屏 (暗色主题) | 347 |
| `MainLayout.vue` | 主布局 (暗色侧边栏) | ~140 |

## 4. 哪些页面可以迁移

| 源页面 | 目标页面 | 迁移方式 | 优先级 |
|---|---|---|---|
| `HomeView.vue` | `PirMonitor.vue` | 改造：JS → TS, 接入 Pinia store + MQTT | 高 |
| `DashboardView.vue` | `Dashboard.vue` | 改造：JS → TS, 接入 vue-echarts | 高 |
| `DeviceView.vue` | `DeviceManage.vue` | 改造：JS → TS, 复用 target API | 高 |
| `AlarmView.vue` | `AlarmManage.vue` | 改造：fetch() → axios, JS → TS | 高 |
| `RecordView.vue` | 合并到 `PirMonitor.vue` | 与 HomeView 合并为一个 PIR 监测页 | 中 |
| `ScreenView.vue` | `ScreenView.vue` | 改造：移除全局样式泄露, JS → TS | 中 |
| `LoginView.vue` | **不迁移** | 目标项目已有自己的登录页 | - |
| `RegisterView.vue` | **不迁移** | 目标项目已有自己的注册页 | - |
| `MainLayout.vue` | **不迁移** | 目标项目有 MonitorLayout 等 4 套布局 | - |

## 5. 哪些组件可以复用

源项目组件较少，仅有一个未使用的 `HelloWorld.vue`。**不需要迁移组件**。

目标项目已有丰富的组件和 composable：
- `MqttStatus.vue` — MQTT 连接状态
- `NotificationBell.vue` — 告警通知铃铛
- `CameraPlayer.vue` — 摄像头播放器
- `FaceCapturePanel.vue` — 人脸抓拍面板
- `useMqtt.ts` — MQTT composable
- `useFaceCapture.ts` — 人脸抓拍 composable

**结论**：新页面优先复用目标项目已有的组件和 composable。

## 6. 哪些 API 需要适配

源项目的 API 端点：

| 源 API 模块 | 端点 | 方法 |
|---|---|---|
| `request.js` | Axios 实例 baseURL=/api | - |
| `device.js` | `/devices/{id}/status`, `/devices/{id}/defense/on`, `/devices/{id}/defense/off`, `/devices`, `/devices/{id}` | GET/POST/PUT/DELETE |
| `alarm.js` | `/alarms?limit=` | GET |
| `record.js` | `/records?limit=` | GET |
| (内联 fetch) | `/alarms/{id}/handle` | POST |
| (内联 fetch) | `/stats` | GET |

目标项目已有更完善的 API 封装：
- `src/api/device.ts` — 设备 CRUD + 布防/撤防
- `src/api/alarm.ts` — 告警列表 + 处理
- `src/api/activity.ts` — 人体感应记录
- `src/api/chat.ts` — AI 对话
- `src/api/face.ts` — 人脸识别
- `src/api/statistics.ts` — 统计数据
- `src/api/camera.ts` — 摄像头

**结论**：新增 `src/api/humanSecurity.ts` 作为聚合适配层，内部复用目标项目已有 API 模块。

## 7. 哪些样式需要迁移

- 源项目所有样式均为 `<style scoped>`，不会与目标项目冲突
- `ScreenView.vue` 有一个全局 `<style>` 块会污染页面，需改为 scoped
- 目标项目全局样式在 `src/style.css` 中
- Element Plus 主题在目标项目中统一

## 8. 哪些依赖需要安装

**无需安装新依赖！** 两个项目使用完全相同的技术栈：
- vue, vue-router, pinia, element-plus, @element-plus/icons-vue, echarts, axios

源项目没有而目标项目有的依赖（MQTT），新模块不需要。

## 9. 哪些文件不能直接复制

| 文件 | 原因 |
|---|---|
| `src/main.js` | 目标项目有自己的 main.ts，包含 Pinia + Icon 注册 |
| `src/App.vue` | 目标项目有自己的 App.vue |
| `src/router/index.js` | 路由模式不同 (Hash vs History)，需合并而非替换 |
| `src/layout/MainLayout.vue` | 目标项目有 4 套角色布局 |
| `src/api/request.js` | 目标项目有自己的 axios 实例 (带 token 拦截器) |
| `src/api/device.js` | 目标项目已有更完善的 device API |
| `LoginView.vue` / `RegisterView.vue` | 目标项目已有自己的登录注册页 |
| `node_modules/` | 绝对不复制 |
| `dist/` | 不复制 |
| `index.html` | 目标项目有自己的 index.html |
| `vite.config.js` | 目标项目使用 vite.config.ts (含 SSL) |

## 10. 最终融合方案

### 10.1 新增目录结构

```
src/views/human-security/
├── Index.vue              ← 新建：功能卡片入口页
├── Dashboard.vue          ← 改造自源 DashboardView.vue
├── AiAssistant.vue        ← 新建：AI 智能助手 (复用 chatStore)
├── DeviceManage.vue       ← 改造自源 DeviceView.vue
├── PirMonitor.vue         ← 改造自源 HomeView.vue + RecordView.vue
├── AlarmManage.vue        ← 改造自源 AlarmView.vue
├── FaceRecognition.vue    ← 新建：人脸识别 (复用 faceStore)
├── MessageCenter.vue      ← 新建：消息中心 (点位页)
├── EmergencyHelp.vue      ← 新建：紧急求助 (点位页)
├── ScreenView.vue         ← 改造自源 ScreenView.vue
└── components/            ← 预留：模块内共享组件
```

### 10.2 新增路由

```typescript
// 在 /human-security 下新增路由组
{
  path: '/human-security',
  component: HumanSecurityLayout,  // 或复用 MonitorLayout
  meta: { auth: true },
  children: [
    { path: '', name: 'HumanSecurity', component: HumanSecurityIndex },
    { path: 'dashboard', name: 'HSDashboard', component: HSDashboard },
    { path: 'ai', name: 'HSAiAssistant', component: HSAiAssistant },
    { path: 'device', name: 'HSDeviceManage', component: HSDeviceManage },
    { path: 'pir', name: 'HSPirMonitor', component: HSPirMonitor },
    { path: 'alarm', name: 'HSAlarmManage', component: HSAlarmManage },
    { path: 'face', name: 'HSFaceRecognition', component: HSFaceRecognition },
    { path: 'message', name: 'HSMessageCenter', component: HSMessageCenter },
    { path: 'help', name: 'HSEmergencyHelp', component: HSEmergencyHelp },
    { path: 'screen', name: 'HSScreenView', component: HSScreenView },
  ]
}
```

### 10.3 新增菜单

在 AdminLayout 和 MonitorLayout 中新增菜单项"智慧安防控制台"，跳转 `/human-security`。

### 10.4 新增 API 文件

`src/api/humanSecurity.ts` — 聚合适配层，复用目标项目已有 API。

### 10.5 改造原则

1. JavaScript → TypeScript (`<script setup>` → `<script setup lang="ts">`)
2. 相对路径 → `@/` 别名
3. `fetch()` → axios (复用 `src/api/index.ts` 实例)
4. `echarts.init()` → 保持直接用法 (目标项目也这样用)
5. 硬编码设备编号 → 支持参数化
6. localStorage → Pinia auth store
7. 所有 API 使用 `/api` 相对路径

### 10.6 不破坏原有功能

- 不修改原 router 中的 `/resident`, `/guard`, `/admin`, `/monitor` 路由
- 不修改原 layout 组件逻辑
- 不修改原 store, API, 组件
- 不修改 `main.ts`, `App.vue`
- 仅做**增量添加**
