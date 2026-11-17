@echo off
title Smart Security - Starting...
echo ===========================================
echo   Smart Security - Starting all services
echo ===========================================
echo.

set ROOT=%~dp0

echo [1/3] Face Recognition (port 5000)...
start "Face-Python" /MIN cmd /c "cd /d %ROOT%services\face-microservice && python web_app.py"
echo   Loading models (30-60s)...
:wait_face
timeout /t 5 /nobreak >nul
python -c "import requests; requests.get('http://localhost:5000/api/status',timeout=2)" >nul 2>nul
if errorlevel 1 goto wait_face
echo   OK

echo [2/3] Backend (port 8080)...
start "Backend" /MIN cmd /c "cd /d %ROOT%human-security-backend && mvn spring-boot:run"
echo   Compiling and starting (20-30s)...
:wait_backend
timeout /t 5 /nobreak >nul
python -c "import requests; requests.get('http://localhost:8080/api/face/status',timeout=3)" >nul 2>nul
if errorlevel 1 goto wait_backend
echo   OK

echo [3/3] Frontend (port 5177)...
if not exist "%ROOT%pir-monitor-frontend\pir-monitor-frontend\node_modules" (
    echo   ERROR: node_modules missing!
    echo   Run: cd pir-monitor-frontend\pir-monitor-frontend ^&^& npm install
    pause
    exit /b 1
)
start "Frontend" /MIN cmd /c "cd /d %ROOT%pir-monitor-frontend\pir-monitor-frontend && npm run dev"
echo   Starting (5-10s)...
:wait_frontend
timeout /t 3 /nobreak >nul
python -c "import requests; requests.get('http://localhost:5177',timeout=2)" >nul 2>nul
if errorlevel 1 goto wait_frontend
echo   OK

echo.
echo ===========================================
echo   Ready! http://localhost:5177
echo   Login: admin / 123456
echo ===========================================
echo.
pause
start http://localhost:5177

