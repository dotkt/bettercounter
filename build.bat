@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
echo ========================================
echo BetterCounter 一键编译脚本
echo ========================================
echo.

REM Java 环境路径（由 setup_java.bat 自动检测并写入）
REM 如果路径不正确，请运行 setup_java.bat 重新检测
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

REM 验证 JAVA_HOME 是否有效
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ========================================
    echo 错误: JAVA_HOME 指向无效目录
    echo ========================================
    echo.
    echo 当前 JAVA_HOME: %JAVA_HOME%
    echo.
    echo 请运行 setup_java.bat 自动检测并配置 Java 路径
    echo.
    pause
    exit /b 1
)

echo 使用 Java: %JAVA_HOME%
echo.

REM 检查git是否可用
git --version >nul 2>&1
if errorlevel 1 (
    echo 警告: 未检测到git，将使用"unknown"作为commit hash
) else (
    echo 当前Git Commit: 
    git rev-parse --short=6 HEAD
    echo.
)

echo 开始编译...
echo.

REM 使用gradlew.bat进行编译
call gradlew.bat assembleDebug

if errorlevel 1 (
    echo.
    echo ========================================
    echo 编译失败！
    echo ========================================
    exit /b 1
) else (
    echo.
    echo ========================================
    echo 编译成功！
    echo ========================================
    echo APK位置: app\build\outputs\apk\debug\app-debug.apk
    echo.
)
