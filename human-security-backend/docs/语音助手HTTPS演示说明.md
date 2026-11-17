# 智慧安防 AI 语音助手 — HTTPS 远程演示说明

## 一、为什么需要 HTTPS

浏览器 `SpeechRecognition` (Web Speech API) 要求页面在安全环境下运行。安全环境包括：

- `https://` 协议（包括 cloudflared / ngrok 隧道）
- `http://localhost` / `http://127.0.0.1`

普通局域网 HTTP (`http://192.168.x.x`) 不支持语音识别，但文字问答和手动说话可用。

---

## 二、本机 localhost 使用方式

```bash
# 1. 启动 Python 人脸服务
cd E:\develop\FaceMicroservice_Delivery\code
.venv\Scripts\activate
python web_app.py

# 2. 另开终端，启动 Spring Boot
cd E:\develop\claude\human-security-backend
mvn spring-boot:run

# 3. 浏览器打开
http://localhost:8080
```

---

## 三、cloudflared HTTPS 隧道（推荐，免费）

### 安装 cloudflared

```bash
# Windows (以管理员运行 PowerShell)
winget install Cloudflare.cloudflared

# 或下载 exe:
# https://github.com/cloudflare/cloudflared/releases
```

### 启动隧道

```bash
# 确保 Spring Boot 已在 8080 端口运行
cloudflared tunnel --url http://localhost:8080
```

输出示例：
```
2025-07-09T10:00:00Z INF Requesting new quick Tunnel on trycloudflare.com...
2025-07-09T10:00:05Z INF +-----------------------------------------------------------+
2025-07-09T10:00:05Z INF |  Your quick Tunnel has been created! Visit it at:       |
2025-07-09T10:00:05Z INF |  https://xxxx.trycloudflare.com                          |
2025-07-09T10:00:05Z INF +-----------------------------------------------------------+
```

复制 `https://xxxx.trycloudflare.com` 发给手机或另一台电脑访问。

---

## 四、ngrok HTTPS 隧道

### 安装 ngrok

```bash
# Windows
winget install ngrok

# 注册 https://ngrok.com 获取 authtoken
ngrok config add-authtoken <你的token>
```

### 启动隧道

```bash
ngrok http 8080
```

输出示例：
```
Forwarding  https://xxxx.ngrok-free.app -> http://localhost:8080
```

---

## 五、手机访问测试步骤

1. 确保电脑上 Python 人脸服务、Spring Boot 后端均已启动。
2. 启动 cloudflared 或 ngrok 隧道。
3. 将 HTTPS 地址发给手机（微信/短信/邮件）。
4. 手机浏览器打开该地址。
5. **允许麦克风权限**（浏览器会弹出询问）。
6. 点击右下角浮球 → 进入「AI 智能助手 / 小安」。
7. 点击「开启助手」→ 允许麦克风。
8. 说「小安小安，打开实时检测」→ 允许摄像头权限。
9. 观察页面是否切换到实时检测并显示摄像头画面。

---

## 六、麦克风权限说明

- Chrome/Edge：地址栏左侧锁图标 → 网站设置 → 麦克风 → 允许。
- Safari：设置 → Safari → 麦克风 → 允许。
- 如果权限被拒绝，页面会提示：「麦克风权限被拒绝，请在浏览器地址栏允许麦克风权限。」

---

## 七、摄像头权限说明

- 实时检测功能需要摄像头权限。
- Chrome/Edge：地址栏左侧锁图标 → 网站设置 → 摄像头 → 允许。
- 如果权限被拒绝，页面会提示：「摄像头权限被拒绝，请在浏览器地址栏允许摄像头权限。」

---

## 八、常见问题排查

| 问题 | 原因 | 解决 |
|------|------|------|
| 语音助手无法启动 | 非 HTTPS 非 localhost | 使用 cloudflared/ngrok 隧道或 localhost |
| 麦克风没反应 | 浏览器权限未允许 | 地址栏锁图标 → 允许麦克风 |
| 人脸识别不可用 | Python 服务未启动 | 启动 `python web_app.py` |
| 页面打开空白 | Spring Boot 未启动 | `mvn spring-boot:run` |
| 摄像头黑屏 | 摄像头权限未允许 | 地址栏锁图标 → 允许摄像头 |
| 语音播报不响 | TTS 服务不可用 | 浏览器会自动换用 speechSynthesis 兜底 |

---

## 九、推荐演示命令

```bash
# 终端 1: Python 人脸服务
cd E:\develop\FaceMicroservice_Delivery\code && .venv\Scripts\activate && python web_app.py

# 终端 2: Spring Boot
cd E:\develop\claude\human-security-backend && mvn spring-boot:run

# 终端 3: HTTPS 隧道
cloudflared tunnel --url http://localhost:8080
```

然后手机打开隧道地址，开始演示。
