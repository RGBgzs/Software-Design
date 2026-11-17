@echo off
chcp 65001 >nul 2>&1
cd /d "%~dp0"

echo ============================================================
echo   FaceRecognitionMicroservice v1.0.0
echo   人脸识别 + 活体检测 + TTS 语音播报
echo ============================================================
echo.
echo   API Base : http://localhost:5000
echo   前端页面 : http://localhost:5000/frontend
echo   健康检查 : http://localhost:5000/api/face/status
echo.
echo   环境变量（可选）:
echo     set FACE_SERVER_PORT=8080        # 修改端口
echo     set FACE_SPRING_BOOT_URL=...     # 修改 Spring Boot 地址
echo     set FACE_ENABLE_SPRING_SYNC=0    # 关闭同步
echo     set FACE_ENABLE_TTS=0            # 关闭语音播报
echo ============================================================
echo.

set PYTHONIOENCODING=utf-8

echo 正在加载模型（约 5-10 秒）...
python web_app.py

pause
