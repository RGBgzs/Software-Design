# 智慧安防 · Python 人脸识别服务

## 推荐环境
- Python 3.9+
- pip

## 安装依赖

```bash
pip install flask flask-cors
```

## 启动

```bash
python web_app.py
```

端口: 5000

## 健康检查

```http
GET http://localhost:5000/api/face/status
```

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/face/status | 健康检查 |
| POST | /api/face/register | 人脸注册（含质量检测） |
| POST | /api/face/identify | 人脸识别 |
| POST | /api/face/check-duplicate | 重复检测 |
| POST | /api/face/delete | 删除人脸 |

## Spring Boot 调用方式

Spring Boot 通过 `FaceRemoteClient` (RestTemplate) 调用本服务。
配置在 `application.yml` 中的 `face.service.base-url`。
默认地址: http://localhost:5000

## 模型文件

本项目使用 **Mock 模式**，不需要下载大型 AI 模型。
增强版已实现模拟的质量检测、重复检测、健康检查等功能。

如需真实人脸识别，需配置:
- MTCNN 人脸检测模型
- InsightFace/ArcFace 人脸识别模型
- MiniFASNetV2 活体检测模型

## 摄像头权限

浏览器访问时需授予摄像头权限。
如果权限被拒绝，请在浏览器地址栏点击锁图标开启。
