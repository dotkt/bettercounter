# Intent API 实现方案

## 概述

通过 Android Intent 实现电脑远程控制 BetterCounter app，支持：
1. **添加计数器** - 通过 Intent 添加新计数器
2. **增加/减少计数** - 增加或减少计数器条目
3. **删除计数器** - 删除指定计数器
4. **导入/导出数据** - 批量导入导出数据

## Intent Actions

| Action | 功能 | 必填参数 |
|--------|------|----------|
| `org.kde.bettercounter.ADD_COUNTER` | 添加计数器 | `name` |
| `org.kde.bettercounter.INCREMENT_COUNTER` | 增加计数 | `name`, 可选 `count` |
| `org.kde.bettercounter.DECREMENT_COUNTER` | 减少计数 | `name` |
| `org.kde.bettercounter.ADD_TIMESTAMP` | 添加时间戳 | `name`, `timestamp` |
| `org.kde.bettercounter.DELETE_TIMESTAMP` | 删除时间戳范围 | `name`, `since`, `until` |
| `org.kde.bettercounter.DELETE_COUNTER` | 删除计数器 | `name` |
| `org.kde.bettercounter.EXPORT_DATA` | 导出数据 | 无 |
| `org.kde.bettercounter.IMPORT_DATA` | 导入数据 | `file_path` |

## Intent 参数

### ADD_COUNTER (添加计数器)

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `name` | String | 是 | - | 计数器名称 |
| `category` | String | 否 | "默认" | 分类 |
| `color` | String | 否 | #2196F3 | 颜色代码 (#RRGGBB) |
| `interval` | String | 否 | DAY | 间隔 |
| `goal` | Int | 否 | 1 | 目标值 |

**interval 可选值：** DAY, WEEK, MONTH, YEAR, HOUR, TIMER, LIFETIME

### INCREMENT_COUNTER (增加计数)

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `name` | String | 是 | - | 计数器名称 |
| `count` | Int | 否 | 1 | 增加次数 |

### DECREMENT_COUNTER (减少计数)

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `name` | String | 是 | 计数器名称 |

### ADD_TIMESTAMP (添加时间戳)

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `name` | String | 是 | - | 计数器名称 |
| `timestamp` | Long | 否 | 当前时间 | Unix 时间戳（毫秒） |
| `count` | Int | 否 | 1 | 添加相同时间戳的次数 |

**示例 timestamp 值：**
- `1704067200000` = 2024-01-01 00:00:00 UTC
- `1704153600000` = 2024-01-02 00:00:00 UTC

### DELETE_TIMESTAMP (删除时间戳范围)

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| `name` | String | 是 | - | 计数器名称 |
| `since` | Long | 否 | 0 | 开始时间 Unix 时间戳（毫秒） |
| `until` | Long | 否 | 当前时间 | 结束时间 Unix 时间戳（毫秒） |

### DELETE_COUNTER (删除计数器)

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `name` | String | 是 | 计数器名称 |

### IMPORT_DATA (导入数据)

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `file_path` | String | 是 | 文件完整路径 |

### EXPORT_DATA (导出数据)

无需参数，导出到 `/sdcard/Download/bettercounter_export.csv`

## ADB 命令示例

> 注意：debug 版本的包名是 `org.kde.bettercounter.debug`

### 添加计数器

```bash
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.ADD_COUNTER \
  --es name "喝水"

# 完整参数
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.ADD_COUNTER \
  --es name "运动" \
  --es category "健康" \
  --es color "#FF5722" \
  --es interval "WEEK" \
  --ei goal 3
```

### 增加计数

```bash
# 增加 1 次
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.INCREMENT_COUNTER \
  --es name "喝水"

# 增加 3 次
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.INCREMENT_COUNTER \
  --es name "喝水" \
  --ei count 3
```

### 减少计数

```bash
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.DECREMENT_COUNTER \
  --es name "喝水"
```

### 添加时间戳

```bash
# 添加当前时间戳（1次）
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "喝水"

# 添加指定时间戳（2024-01-01 00:00:00 UTC = 1704067200000）
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "喝水" \
  --el timestamp 1704067200000

# 添加指定时间戳，添加3次
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "喝水" \
  --el timestamp 1704067200000 \
  --ei count 3

# 添加多个不同时间戳（分多次调用）
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "喝水" \
  --el timestamp 1704067200000

adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "喝水" \
  --el timestamp 1704153600000

adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "喝水" \
  --el timestamp 1704240000000
```

### 删除时间戳范围

```bash
# 删除 2024-01-01 当天的所有记录
# 2024-01-01 00:00:00 UTC = 1704067200000
# 2024-01-02 00:00:00 UTC = 1704153600000
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.DELETE_TIMESTAMP \
  --es name "喝水" \
  --el since 1704067200000 \
  --el until 1704153600000

# 删除最近1小时的所有记录
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.DELETE_TIMESTAMP \
  --es name "喝水" \
  --el since 0 \
  --el until $(date +%s000)
```

### 删除计数器

```bash
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.DELETE_COUNTER \
  --es name "喝水"
```

### 导出数据

```bash
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.EXPORT_DATA
```

### 导入数据

```bash
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.IMPORT_DATA \
  --es file_path "/sdcard/Download/import.csv"
```

## 完整测试脚本示例

```bash
#!/bin/bash
DEVICE="192.168.110.62:5555"
PACKAGE="org.kde.bettercounter.debug"
ACTIVITY="org.kde.bettercounter.ui.MainActivity"

echo "=== 1. 添加计数器 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.ADD_COUNTER \
  --es name "测试喝水" --es category "测试"

echo "=== 2. 增加计数 3 次 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.INCREMENT_COUNTER \
  --es name "测试喝水" --ei count 3

echo "=== 3. 导出数据 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.EXPORT_DATA

echo "=== 4. 拉取导出文件 ==="
adb -s $DEVICE pull /sdcard/Download/bettercounter_export.csv ./

echo "=== 完成 ==="
cat bettercounter_export.csv
```

## 注意事项

1. **包名**：debug 版本的包名是 `org.kde.bettercounter.debug`，release 版本是 `org.kde.bettercounter`
2. **singleTop 模式**：如果 app 已在前台运行，会通过 `onNewIntent()` 接收新的 Intent
3. **权限**：导出功能需要存储权限，首次使用需授权
4. **颜色格式**：支持 #RRGGBB 格式
5. **不影响现有功能**：Widget 点击等现有功能不受影响
