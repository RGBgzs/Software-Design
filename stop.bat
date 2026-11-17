@echo off
chcp 65001 >nul
title Stopping...
echo Stopping all services...
taskkill /f /im python.exe >nul 2>&1 && echo   OK Python || echo   No Python
taskkill /f /im java.exe >nul 2>&1 && echo   OK Java || echo   No Java
taskkill /f /im node.exe >nul 2>&1 && echo   OK Node || echo   No Node
echo Done.
pause

