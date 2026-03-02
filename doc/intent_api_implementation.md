# BetterCounter Intent API 文档

## 概述

通过 Android Intent 实现电脑远程控制 BetterCounter app，支持：
- 添加/删除计数器
- 增加/减少计数
- 添加/删除时间戳
- 导入/导出数据

## 设备信息

- **设备地址:** `192.168.110.62:5555`
- **包名 (Debug):** `org.kde.bettercounter.debug`
- **包名 (Release):** `org.kde.bettercounter`

## Intent Actions 列表

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

## 参数说明

### ADD_COUNTER

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | - | 计数器名称 (必填) |
| `category` | String | "默认" | 分类 |
| `color` | String | #2196F3 | 颜色 (#RRGGBB) |
| `interval` | String | DAY | 间隔 (DAY/WEEK/MONTH/YEAR/HOUR/TIMER/LIFETIME) |
| `goal` | Int | 1 | 目标值 |

### INCREMENT_COUNTER / DECREMENT_COUNTER

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | - | 计数器名称 (必填) |
| `count` | Int | 1 | 增加次数 (仅 INCREMENT) |

### ADD_TIMESTAMP

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | - | 计数器名称 (必填) |
| `timestamp` | Long | 当前时间 | Unix 毫秒时间戳 |
| `count` | Int | 1 | 添加相同时间戳的次数 |

### DELETE_TIMESTAMP

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | - | 计数器名称 (必填) |
| `since` | Long | 0 | 开始时间 (Unix 毫秒) |
| `until` | Long | 当前时间 | 结束时间 (Unix 毫秒) |

### DELETE_COUNTER

| 参数 | 类型 | 说明 |
|------|------|------|
| `name` | String | 计数器名称 (必填) |

### IMPORT_DATA

| 参数 | 类型 | 说明 |
|------|------|------|
| `file_path` | String | 文件完整路径 (必填) |

### EXPORT_DATA

无需参数，导出到 `/sdcard/Download/bettercounter_export.csv`

---

## ADB 命令示例

> 基础命令格式:
> ```bash
> adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity -a <ACTION> [参数...]
> ```

### 1. 添加计数器 (ADD_COUNTER)

```bash
# 基本用法
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

### 2. 增加计数 (INCREMENT_COUNTER)

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

### 3. 减少计数 (DECREMENT_COUNTER)

```bash
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.DECREMENT_COUNTER \
  --es name "喝水"
```

### 4. 添加时间戳 (ADD_TIMESTAMP)

```bash
# 添加当前时间戳
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "喝水"

# 添加指定时间戳 (2026-03-01 00:00:00 UTC = 1774550400000)
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "喝水" \
  --el timestamp 1774550400000

# 添加指定时间戳 3 次
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "喝水" \
  --el timestamp 1774550400000 \
  --ei count 3

# 添加多个不同时间戳 (多次调用)
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "喝水" --el timestamp 1774550400000

adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "喝水" --el timestamp 1774636800000
```

### 5. 删除时间戳范围 (DELETE_TIMESTAMP)

```bash
# 删除 2026-03-01 当天的所有记录
# 2026-03-01 00:00:00 UTC = 1774550400000
# 2026-03-02 00:00:00 UTC = 1774636800000
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.DELETE_TIMESTAMP \
  --es name "喝水" \
  --el since 1774550400000 \
  --el until 1774636800000
```

### 6. 删除计数器 (DELETE_COUNTER)

```bash
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.DELETE_COUNTER \
  --es name "喝水"
```

### 7. 导出数据 (EXPORT_DATA)

```bash
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.EXPORT_DATA

# 导出后文件位置: /sdcard/Download/bettercounter_export.csv

# 拉取到本地
adb -s 192.168.110.62:5555 pull /sdcard/Download/bettercounter_export.csv ./
```

### 8. 导入数据 (IMPORT_DATA)

```bash
# 先推送导入文件到手机
adb -s 192.168.110.62:5555 push import.csv /sdcard/Download/import.csv

# 执行导入
adb -s 192.168.110.62:5555 shell am start -n org.kde.bettercounter.debug/org.kde.bettercounter.ui.MainActivity \
  -a org.kde.bettercounter.IMPORT_DATA \
  --es file_path "/sdcard/Download/import.csv"
```

---

## 常用时间戳参考

| 日期时间 | Unix 毫秒 |
|----------|-----------|
| 2026-01-01 00:00 UTC | 1767225600000 |
| 2026-02-01 00:00 UTC | 1772640000000 |
| 2026-03-01 00:00 UTC | 1774550400000 |
| 2026-03-02 00:00 UTC | 1774636800000 |
| 2026-04-01 00:00 UTC | 1777142400000 |

---

## 完整测试脚本

```bash
#!/bin/bash
DEVICE="192.168.110.62:5555"
PACKAGE="org.kde.bettercounter.debug"
ACTIVITY="org.kde.bettercounter.ui.MainActivity"

echo "=== 1. 添加计数器 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.ADD_COUNTER \
  --es name "喝水" --es category "测试"

echo "=== 2. 增加计数 3 次 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.INCREMENT_COUNTER \
  --es name "喝水" --ei count 3

echo "=== 3. 添加历史时间戳 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "喝水" --el timestamp 1774550400000

echo "=== 4. 删除历史时间戳 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DELETE_TIMESTAMP \
  --es name "喝水" --el since 1774550400000 --el until 1774636800000

echo "=== 5. 减少计数 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DECREMENT_COUNTER \
  --es name "喝水"

echo "=== 6. 导出数据 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.EXPORT_DATA

sleep 2

echo "=== 7. 拉取导出文件 ==="
adb -s $DEVICE pull /sdcard/Download/bettercounter_export.csv ./

echo "=== 完成 ==="
cat bettercounter_export.csv
```

---

## 注意事项

1. **首次导出需要授权**: 首次使用导出功能时，手机会弹出存储权限授权对话框，需要用户手动授权
2. **参数简写**: 所有 String 类型参数都支持简写形式（如 `name` 代替 `EXTRA_COUNTER_NAME`）
3. **时间戳格式**: 所有时间相关参数使用 Unix 毫秒时间戳
4. **singleTop 模式**: app 使用 `singleTop` 模式，已在后台运行时不会重新创建 Activity
