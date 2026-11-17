# 智慧安防 AI 语音系统（smart_guard）

软件综合设计课程项目 · 2026.6.29

小组成员：李轲、苗彪彪、魏宸希

---

## 一、项目概述

面向社区与园区场景的一体化智慧安防系统。系统通过 MQTT 接入 PIR 人体感应器、安防终端与摄像头，由 Spring Boot 后端完成设备管理、数据采集、告警处理与权限控制，Vue 3 前端提供管理后台与可视化大屏，Python 微服务负责人脸识别、活体检测与语音合成，并接入 DeepSeek 大模型实现自然语言安防助手。

一句话概括：**设备上报 → 后端研判入库 → 大屏展示与告警 → 语音/文字助手查询与控制**。

## 二、核心功能

| 功能 | 说明 |
|------|------|
| 角色与权限 | 管理员、保安、居民三类角色，各自独立的工作台与菜单；支持注册、登录、个人信息维护 |
| 设备管理 | 设备台账、在线状态、布防/撤防指令下发、离线检测（默认 10 秒轮询、30 秒阈值判定离线） |
| PIR 人体感应 | 感应数据上报与实时状态、检测记录分页查询、按日统计、最近一次检测详情 |
| 告警中心 | 告警记录、类型与等级、处理状态流转、AI 风险研判（规则引擎打分 + 可选大模型复核） |
| 人脸识别 | 人脸注册/删除/批量注册、1:N 识别、活体检测；识别结果回传后端入库，并向终端语音播报 |
| AI 语音助手 | 自然语言查询告警、设备、人员、PIR 与整体安防态势，可执行布防/撤防等动作；大模型不可用时自动回退规则引擎 |
| 知识库问答 | 安防知识条目的增删改查与知识问答对话 |
| 消息中心 | 系统消息与告警消息推送、未读提醒（通知铃铛） |
| 紧急求助 | 紧急联系人维护、SOS 求助事件上报与接收人通知 |
| 可视化大屏 | ECharts 统计图表、摄像头画面、PIR 监测页、监控大屏轮播 |

## 三、系统架构

```
安防终端 / PIR 传感器 ──MQTT──▶ EMQX Broker ──▶ Spring Boot 后端 ──▶ MySQL
                                        │              │
                                        │              ├──▶ 人脸微服务（HTTP，识别/活体/TTS）
                                        │              └──▶ DeepSeek API（可选，语音助手与风险研判）
                                        │
                    Vue 3 前端（HTTP + MQTT over WebSocket）◀──┘
```

| 模块 | 技术栈 | 端口 |
|------|--------|------|
| `human-security-backend` | Spring Boot 3 · MyBatis · MySQL · Eclipse Paho MQTT | 8080 |
| `pir-monitor-frontend` | Vue 3 · TypeScript · Vite · Element Plus · Pinia · ECharts · mqtt.js | 5177 |
| `services/face-microservice` | Python · Flask · PyTorch · MTCNN · ONNX Runtime · TTS | 5000 |

## 四、目录说明

```
smart_guard/
├─ human-security-backend/                  Spring Boot 后端主服务
│  ├─ src/main/java/com/example/security/
│  │  ├─ controller/                        18 个 REST 控制器（鉴权、设备、告警、PIR、消息、SOS、知识库、AI 等）
│  │  ├─ service/                           业务逻辑（设备离线检测、MQTT 收发、语音指令、AI 研判、知识问答）
│  │  ├─ mapper/ · entity/ · dto/           MyBatis 映射、数据库实体、请求/响应对象
│  │  ├─ mqtt/                              MQTT 订阅端，接收设备上报数据
│  │  └─ config/                            数据源、跨域、MQTT、静态资源、数据初始化配置
│  ├─ src/main/java/com/example/securityagent/
│  │                                        人脸模块集成层：人脸控制器、远程调用客户端、识别记录与人脸库 Mapper
│  ├─ src/main/resources/
│  │  ├─ application.yml                    服务端口、数据源、MQTT、华为云 IoTDA、人脸服务参数
│  │  ├─ static/                            构建好的前端页面，随 jar 一起发布
│  │  └─ sql/ · db/migration/               建表、初始化数据与增量迁移脚本
│  ├─ src/test/java/                         MQTT 发布测试工具
│  ├─ sql/                                  PIR 监测相关的建表脚本
│  └─ docs/                                 接口文档、运行说明、融合分析与验收报告
├─ pir-monitor-frontend/pir-monitor-frontend/
│  ├─ src/api/                              后端接口封装（鉴权、设备、告警、人脸、消息、统计等）
│  ├─ src/stores/                           Pinia 状态管理（登录态、设备、告警、MQTT、语音动作）
│  ├─ src/views/                            按角色划分的页面
│  │  ├─ unified/                           统一入口与侧边栏、顶栏、状态标签等公共组件
│  │  ├─ human-security/                    安防主工作台：大屏、设备、告警、PIR、人脸、知识库、消息、SOS
│  │  ├─ admin/ · guard/ · resident/ · monitor/  管理员、保安、居民、监控四套视图
│  │  └─ common/ · login/                   摄像头组件与登录注册页
│  ├─ src/assistant/                        语音助手前端引擎：意图识别、能力注册、动作执行、安全护栏
│  ├─ src/composables/                      useMqtt / useFaceCapture / useVoiceAssistant 等组合式函数
│  └─ src/components/                       摄像头播放、人脸采集、MQTT 状态、通知铃铛等共享组件
├─ services/face-microservice/
│  ├─ config.py                             模型路径、识别阈值、服务端口、Spring 同步开关
│  ├─ face_system.py                        人脸检测与识别核心（MTCNN 检测、特征提取与比对）
│  ├─ web_app.py                            Flask 接口层：注册/识别/活体/用户管理/TTS/同步状态
│  ├─ tts_engine.py                         语音合成引擎，识别到人员后播报问候
│  ├─ spring_sync.py                        识别结果回传 Spring Boot 的同步管理器
│  ├─ static/frontend.html                  人脸采集与注册的网页调试界面
│  ├─ models/                               识别、检测、活体、TTS 的模型文件与词典资源
│  └─ install.bat · start_server.bat · start_web.bat   依赖安装与启动脚本
├─ docs/                                    人脸服务运行说明
├─ setup.bat                                环境自检（Java / Maven / Python / Node / MySQL）
├─ start.bat                                一键启动 Python → 后端 → 前端
├─ stop.bat                                 停止全部服务
├─ 使用说明.txt                              快速上手与常见问题
└─ 打包部署指南.md                            打包、部署与测试账号
```

## 五、后端接口分组

| 分组 | 控制器 | 主要能力 |
|------|--------|----------|
| 鉴权 | `AuthController` | 登录、注册、用户信息 |
| 设备 | `DeviceController` · `DeviceStatusController` · `DeviceDefenseController` | 台账、在线状态、布防撤防 |
| PIR 监测 | `PirController` · `MqttDataController` | 状态、记录、统计、上报入口 |
| 告警 | `AlarmRecordController` | 告警查询、处理、统计 |
| 人脸 | `securityagent/FaceController` · `FaceService` | 人员库、识别记录、远程调用人脸微服务 |
| AI 助手 | `AiAgentController` · `AiJudgeController` · `VoiceCommandController` | 对话、风险研判、语音指令解析 |
| 知识库 | `KnowledgeManageController` · `KbChatController` | 条目管理与问答 |
| 消息与求助 | `MessageController` · `SosController` · `SosAlertController` · `ContactController` | 消息推送、SOS 事件、紧急联系人 |
| 统计与其他 | `StatsController` · `ChatRecordController` · `HuaweiIotdaController` | 数据统计、对话记录、华为云 IoTDA 转发（默认关闭） |

## 六、快速开始

环境要求：JDK 17+、Maven 3.6+、Python 3.9+、Node.js 18+、MySQL 5.7+、MQTT Broker（如 EMQX）、Chrome 浏览器（语音识别依赖 Web Speech API）。

```bat
:: 1. 环境自检
setup.bat

:: 2. 一键启动（首次会自动安装前端依赖，约 2 分钟）
start.bat

:: 3. 浏览器访问
::    http://localhost:5177
```

单独启动前端：

```bat
cd pir-monitor-frontend\pir-monitor-frontend
npm install
npm run dev
```

### 配置项

`application.yml` 中的凭据通过环境变量注入，启动前按需设置：

| 变量 | 说明 |
|------|------|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | MySQL 连接串与账号（密码必填） |
| `MQTT_HOST` / `MQTT_USERNAME` / `MQTT_PASSWORD` | MQTT Broker 地址与账号 |
| `AI_API_KEY` / `AI_API_URL` / `AI_MODEL` | DeepSeek 接口密钥、地址与模型（未配置时走规则引擎） |
| `HUAWEI_IOTDA_*` | 华为云 IoTDA 转发参数（默认关闭） |
| `FACE_MODEL_DIR` / `FACE_RECOGNITION_MODEL` | 人脸微服务的模型目录与选用的识别模型 |

### 演示账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | 123456 | 管理员 |
| aaa | 123 | 居民 |
| guard | 123456 | 保安 |

## 七、数据链路说明

1. 安防终端与 PIR 传感器通过 MQTT 向 `subscribe-topic` 上报 JSON 数据，后端 `mqtt/MqttSubscriber` 接收并解析。
2. `MqttMessageService` 将人体感应、设备状态写入数据库，并触发告警判定与消息推送。
3. 管理员在设备页布防/撤防时，`MqttPublisherService` 向 `command-topic` 下发指令，QoS 2 保证送达。
4. `DeviceOfflineDetector` 周期检查心跳，超过阈值未上报的设备标记为离线并生成记录。
5. 前端通过 `useMqtt` 订阅 MQTT over WebSocket，实现告警与状态的实时刷新；统计图表由 ECharts 渲染。
6. 人脸链路：前端采集图片 → 人脸微服务 `/api/identify` 比对 → `spring_sync` 回传后端入库 → TTS 播报识别结果。

更多细节见 [`human-security-backend/docs/运行说明.md`](human-security-backend/docs/运行说明.md)、[`human-security-backend/docs/接口文档-PIR监测.md`](human-security-backend/docs/接口文档-PIR监测.md) 与 [`打包部署指南.md`](打包部署指南.md)。
