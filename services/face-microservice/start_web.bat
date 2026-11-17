@echo off
chcp 65001 >nul 2>&1
cd /d "%~dp0"

echo ============================================================
echo   人脸识别网页系统
echo ============================================================
echo.
echo 正在启动服务...
echo 启动后浏览器访问: http://localhost:5000
echo 按 Ctrl+C 停止服务
echo ============================================================
echo.

set PYTHONIOENCODING=utf-8
start "" http://localhost:5000
python web_app.py

pause
