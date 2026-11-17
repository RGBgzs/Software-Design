@echo off
title Environment Check
echo ===========================================
echo   Smart Security - Environment Check
echo ===========================================
echo.

echo [1/6] Java...
where java >nul 2>nul && (echo   OK) || (echo   WARNING: Install JDK 17+)

echo [2/6] Maven...
where mvn >nul 2>nul && (echo   OK) || (echo   WARNING: Install Maven)

echo [3/6] Python...
where python >nul 2>nul && (echo   OK) || (echo   WARNING: Install Python 3.9+)

echo [4/6] Node.js...
where node >nul 2>nul && (echo   OK) || (echo   WARNING: Install Node.js 18+)

echo [5/6] Python packages...
python -c "import flask; import flask_cors; import onnxruntime; import requests; print('   OK')" 2>nul
if errorlevel 1 (
    echo   Installing missing packages...
    cd /d "%~dp0services\face-microservice"
    pip install torch torchvision Pillow numpy facenet-pytorch flask flask-cors flask-socketio onnxruntime sherpa-onnx requests 2>nul
    cd /d "%~dp0"
    echo   OK (installed)
) else (
    echo   OK
)

echo [6/6] Node modules...
if exist "%~dp0pir-monitor-frontend\pir-monitor-frontend\node_modules" (echo   OK) else (
    echo   WARNING: Run npm install first
    echo   cd pir-monitor-frontend\pir-monitor-frontend ^& npm install
)

echo.
echo ===========================================
echo   Done. Run start.bat to launch.
echo ===========================================
pause

