#!/bin/bash

# BetterCounter 一键编译脚本 (Linux/macOS)

echo "========================================"
echo "BetterCounter 一键编译脚本"
echo "========================================"
echo ""

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# 验证 JAVA_HOME 是否已设置
if [ -z "$JAVA_HOME" ]; then
    echo "========================================"
    echo "错误: JAVA_HOME 环境变量未设置"
    echo "========================================"
    echo ""
    echo "请确保已安装Java Development Kit (JDK) 并正确设置了 JAVA_HOME 环境变量。"
    echo "例如，将其添加到您的 ~/.bashrc 或 ~/.zshrc 文件中:"
    echo "export JAVA_HOME=/path/to/your/jdk"
    echo ""
    exit 1
fi

echo "使用 Java: $JAVA_HOME"
echo ""

# 检查git是否可用
if ! command -v git &> /dev/null; then
    echo "警告: 未检测到git，将使用'unknown'作为commit hash"
else
    echo "当前Git Commit:"
    git rev-parse --short=6 HEAD
    echo ""
fi

echo "开始编译..."
echo ""

# 使用gradlew进行编译
./gradlew assembleDebug

# $? 包含了上一个命令的退出码
if [ $? -ne 0 ]; then
    echo ""
    echo "========================================"
    echo "编译失败！"
    echo "========================================"
    exit 1
else
    echo ""
    echo "========================================"
    echo "编译成功！"
    echo "========================================"
    echo "APK位置: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
fi
