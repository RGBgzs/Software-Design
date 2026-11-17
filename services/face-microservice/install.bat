@echo off
chcp 65001 >nul
echo ============================================================
echo   人脸识别系统 — 一键安装依赖
echo ============================================================
echo.

cd /d "%~dp0"

echo [1/2] 安装基础依赖 (torch, Pillow, numpy, facenet-pytorch)...
pip install torch torchvision Pillow numpy facenet-pytorch
if %errorlevel% neq 0 (
    echo.
    echo ⚠️  facenet-pytorch 在线安装失败，尝试本地安装...
    pip install -e "..\facenet-pytorch-master"
)

echo.
echo [2/2] 创建文件夹...
if not exist face_db mkdir face_db
if not exist test_images mkdir test_images

echo.
echo ============================================================
echo   ✅ 安装完成!
echo ============================================================
echo.
echo 使用方法:
echo   1. 把照片放到 face_db 文件夹 (按人名分文件夹)
echo   2. python register.py      (注册人脸)
echo   3. python recognize.py --image 你的照片.jpg  (识别)
echo.
pause
