#!/bin/bash

# ==============================================================================
#  BetterCounter 环境检测与配置脚本 (Linux/macOS)
# ==============================================================================
#
#  此脚本会：
#  1. 检查是否安装了合适的 Java Development Kit (JDK)。
#  2. 如果未安装，尝试使用系统包管理器 (apt) 进行安装。
#  3. 找到 JDK 的安装路径。
#  4. 生成设置 JAVA_HOME 环境变量所需的命令，并指导用户如何应用。
#
# ==============================================================================

echo "========================================"
echo "BetterCounter 环境检测与配置"
echo "========================================"
echo ""

# --- 1. 检测 Java 命令 ---
echo "[1/4] 正在检测 Java 环境..."

# 检查 'java' 命令是否存在
if command -v java &> /dev/null; then
    JAVA_CMD="java"
    echo "  - 找到了 'java' 命令。"
    
    # 检查是否为 JDK (包含编译器 javac)
    if command -v javac &> /dev/null; then
        echo "  - 找到了 Java 编译器 'javac'，环境似乎是 JDK。"
        JAVA_VERSION=$($JAVA_CMD -version 2>&1 | awk -F '"' '/version/ {print $2}')
        echo "  - Java 版本: $JAVA_VERSION"
        
        # 尝试自动查找 JAVA_HOME
        # readlink -f follows symlinks to find the real path
        REAL_JAVA_PATH=$(readlink -f "$(command -v javac)")
        DETECTED_JAVA_HOME=$(dirname "$(dirname "$REAL_JAVA_PATH")")
        
        echo "  - 自动检测到 JAVA_HOME 可能为: $DETECTED_JAVA_HOME"
        
    else
        echo "  - 警告: 找到了 'java' (JRE)，但未找到 'javac' (JDK)。"
        echo "    编译需要完整的 JDK 环境。"
        NEEDS_INSTALL=true
    fi
else
    echo "  - 未找到 'java' 命令。"
    NEEDS_INSTALL=true
fi
echo ""


# --- 2. 如果需要，安装 JDK ---
if [ "$NEEDS_INSTALL" = true ]; then
    echo "[2/4] 尝试安装 JDK..."
    
    # 检查包管理器 (目前仅支持 apt)
    if command -v apt-get &> /dev/null; then
        echo "  - 检测到 'apt' 包管理器。"
        echo "  - 将尝试安装 openjdk-17-jdk (Android 开发常用版本)。"
        echo "  - 这需要管理员权限 (sudo)，可能会提示您输入密码。"
        
        # 请求 sudo 权限
        sudo -v
        if [ $? -ne 0 ]; then
            echo "  - 错误: 获取 sudo 权限失败。无法继续安装。"
            exit 1
        fi
        
        # 更新包列表并安装
        echo "  - 正在更新 apt 仓库..."
        sudo apt-get update || { echo "  - 错误: 'apt-get update' 失败。"; exit 1; }
        
        echo "  - 正在安装 openjdk-17-jdk..."
        sudo apt-get install -y openjdk-17-jdk || { echo "  - 错误: 'apt-get install' 失败。"; exit 1; }
        
        echo "  - JDK 安装成功！"
        
        # 安装后重新检测 JAVA_HOME
        REAL_JAVA_PATH=$(readlink -f "$(command -v javac)")
        DETECTED_JAVA_HOME=$(dirname "$(dirname "$REAL_JAVA_PATH")")
        
    else
        echo "  - 错误: 未检测到 'apt' 包管理器。"
        echo "    请根据您的 Linux 发行版手动安装 JDK (推荐版本 11 或 17)。"
        echo "    例如:"
        echo "      - Fedora/CentOS: sudo dnf install java-17-openjdk-devel"
        echo "      - Arch Linux: sudo pacman -S jdk-openjdk"
        echo ""
        exit 1
    fi
fi
echo ""


# --- 3. 确认 JAVA_HOME 路径 ---
echo "[3/4] 正在确认 JAVA_HOME 路径..."
if [ -z "$DETECTED_JAVA_HOME" ] || [ ! -d "$DETECTED_JAVA_HOME" ]; then
    echo "  - 错误: 无法自动确定 JDK 的安装路径。"
    echo "    请手动找到您的 JDK 安装目录。"
    exit 1
fi
echo "  - 确认路径: $DETECTED_JAVA_HOME"
echo ""

# --- 4. 生成配置指令 ---
echo "[4/4] 请按以下步骤完成配置:"
echo ""
echo "------------------------------------------------------------------------"
echo "  为了让编译脚本能找到 Java，您需要设置 JAVA_HOME 环境变量。"
echo ""
echo "  1. 将下面这行命令添加到您的 shell 配置文件中。"
echo "     (通常是 ~/.bashrc 或 ~/.zshrc)"
echo ""
echo "     export JAVA_HOME=$DETECTED_JAVA_HOME"
echo ""
echo "  2. 为了方便，您也可以将 Java 的 'bin' 目录添加到 PATH 中:"
echo ""
echo "     export PATH=\$JAVA_HOME/bin:\$PATH"
echo ""
echo "  3. 添加后，运行以下命令使配置立即生效 (或重新打开终端):"
echo ""
echo "     source ~/${SHELL##*/rc}"
echo ""
echo "------------------------------------------------------------------------"
echo ""
echo "配置完成后，请重新运行 ./build.sh"
echo ""
