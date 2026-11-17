@echo off
chcp 65001 >nul
echo ========================================
echo  智慧安防 HTTPS 隧道启动脚本
echo ========================================
echo.

REM Check if cloudflared is installed
where cloudflared >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [错误] cloudflared 未安装。
    echo.
    echo 请先安装 cloudflared:
    echo   winget install Cloudflare.cloudflared
    echo.
    echo 或下载: https://github.com/cloudflare/cloudflared/releases
    echo.
    pause
    exit /b 1
)

echo [提示] 请确保 Spring Boot 后端已在 8080 端口运行。
echo [提示] 按 Ctrl+C 可停止隧道。
echo.
echo 正在启动 cloudflared HTTPS 隧道...
echo.

cloudflared tunnel --url http://localhost:8080

pause
