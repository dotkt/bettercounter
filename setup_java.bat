@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
echo ========================================
echo Java 环境检测脚本
echo ========================================
echo.

set "FOUND_JAVA="

REM 1. 检查 Android Studio 的 JDK（最常见）
if exist "%LOCALAPPDATA%\Programs\Android\Android Studio\jbr\bin\java.exe" (
    set "FOUND_JAVA=%LOCALAPPDATA%\Programs\Android\Android Studio\jbr"
    echo 找到 Android Studio JDK: !FOUND_JAVA!
    goto :found
)

REM 2. 检查 Program Files 下的 JDK
for /d %%i in ("C:\Program Files\Java\jdk*") do (
    if exist "%%i\bin\java.exe" (
        set "FOUND_JAVA=%%i"
        echo 找到 JDK: !FOUND_JAVA!
        goto :found
    )
)

REM 3. 检查 Program Files (x86) 下的 JDK
for /d %%i in ("C:\Program Files (x86)\Java\jdk*") do (
    if exist "%%i\bin\java.exe" (
        set "FOUND_JAVA=%%i"
        echo 找到 JDK: !FOUND_JAVA!
        goto :found
    )
)

REM 4. 检查 Program Files 下的 JRE
for /d %%i in ("C:\Program Files\Java\jre*") do (
    if exist "%%i\bin\java.exe" (
        set "FOUND_JAVA=%%i"
        echo 找到 JRE: !FOUND_JAVA!
        goto :found
    )
)

REM 5. 检查 PATH 中的 java 命令
where java >nul 2>&1
if not errorlevel 1 (
    for /f "delims=" %%i in ('where java 2^>nul') do (
        set "JAVA_PATH=%%i"
        REM 从完整路径提取 JAVA_HOME
        for %%j in ("!JAVA_PATH!") do set "JAVA_DIR=%%~dpj"
        set "JAVA_DIR=!JAVA_DIR:~0,-1!"
        for %%k in ("!JAVA_DIR!") do set "JAVA_DIR=%%~dpk"
        set "JAVA_DIR=!JAVA_DIR:~0,-1!"
        if exist "!JAVA_DIR!\bin\java.exe" (
            set "FOUND_JAVA=!JAVA_DIR!"
            echo 从 PATH 找到 Java: !FOUND_JAVA!
            goto :found
        )
    )
)

REM 6. 检查 Eclipse Adoptium JDK
for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-*") do (
    if exist "%%i\bin\java.exe" (
        set "FOUND_JAVA=%%i"
        echo 找到 Eclipse Adoptium JDK: !FOUND_JAVA!
        goto :found
    )
)

REM 如果都没找到
echo.
echo ========================================
echo 错误: 未找到 Java 安装
echo ========================================
echo.
echo 请确保已安装 JDK 17 或更高版本
echo.
pause
exit /b 1

:found
if "!FOUND_JAVA!"=="" (
    echo.
    echo ========================================
    echo 错误: 无法确定 Java 路径
    echo ========================================
    echo.
    pause
    exit /b 1
)

echo.
echo 找到 Java: !FOUND_JAVA!
echo 正在写入 build.bat...
echo.

REM 使用 PowerShell 替换 build.bat 中的 JAVA_HOME 行
powershell -NoProfile -Command "$file = 'build.bat'; $content = Get-Content $file -Raw -Encoding UTF8; $pattern = '(set \"JAVA_HOME=)([^\"]+)(\")'; $replacement = '$1' + '%FOUND_JAVA%' + '$3'; $newContent = $content -replace $pattern, $replacement; Set-Content $file -Value $newContent -NoNewline -Encoding UTF8"

if errorlevel 1 (
    echo.
    echo 警告: 无法自动更新 build.bat
    echo.
    echo 请手动编辑 build.bat，找到这一行:
    echo     set "JAVA_HOME=..."
    echo.
    echo 替换为:
    echo     set "JAVA_HOME=%FOUND_JAVA%"
    echo.
) else (
    echo 已成功更新 build.bat
)

echo ========================================
echo 配置完成！
echo ========================================
echo.
echo Java 路径已写入 build.bat: %FOUND_JAVA%
echo.
echo 现在可以运行 build.bat 进行编译了！
echo.
pause

