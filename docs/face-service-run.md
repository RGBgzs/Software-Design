# 人脸识别微服务说明

## 位置

```
C:\Users\27822\Desktop\智慧安防\services\face-microservice
```

## 启动方式

### 方式一：start-all.bat（推荐）

在项目根目录运行：

```
start-all.bat
```

该脚本会自动依次启动人脸服务和 Spring Boot 后端。

### 方式二：手动启动

```bash
cd services\face-microservice
python web_app.py
```

服务默认监听 `http://127.0.0.1:5000`

### 方式三：使用项目自带脚本

```bash
cd services\face-microservice
start_web.bat
```

## 健康检查

```bash
curl http://127.0.0.1:5000/health
curl http://127.0.0.1:5000/api/face/status
```

## 配置文件

- `config.py` — 人脸识别配置
- `requirements.txt` — Python 依赖

## Spring Boot 对接

Spring Boot 通过 `FaceRemoteClient` 调用 Python 服务：

```yaml
face:
  service:
    enabled: true
    base-url: http://127.0.0.1:5000
    threshold: 0.4
```

## API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/face/status` | GET | 服务状态 |
| `/api/face/identify` | POST | 人脸识别 |
| `/api/face/register` | POST | 注册人脸 |
| `/api/detect-live` | POST | 实时检测 |
| `/api/users` | GET | 用户列表 |
| `/api/users/register` | POST | 注册用户 |
| `/api/users/{name}` | DELETE | 删除用户 |
| `/api/tts/synthesize` | POST | 语音合成 |
| `/api/sync/status` | GET | 同步状态 |

## 依赖安装

```bash
pip install -r requirements.txt
```

## 人脸库位置

- `face_db/` — 注册人脸图片
- `face_embeddings.pkl` — 人脸特征向量缓存
- `models/` — 深度学习模型文件
