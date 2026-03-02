#!/bin/bash
DEVICE="192.168.110.62:5555"
PACKAGE="org.kde.bettercounter.debug"
ACTIVITY="org.kde.bettercounter.ui.MainActivity"

# 计算前3天时间戳 (Unix毫秒)
THREE_DAYS_AGO=$(date -d "3 days ago" +%s000)

echo "=============================================="
echo "测试 1: 英文名称计数器"
echo "=============================================="

echo "=== 1.1 删除旧计数器（如存在）==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DELETE_COUNTER \
  --es name "test" 2>&1 | head -1

sleep 1

echo "=== 1.2 添加英文计数器 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.ADD_COUNTER \
  --es name "test" --es category "test" 2>&1 | head -1

sleep 1

echo "=== 1.3 增加计数 3 次 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.INCREMENT_COUNTER \
  --es name "test" --ei count 3 2>&1 | head -1

sleep 1

echo "=== 1.4 添加历史时间戳（固定日期: 2026-03-01）==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "test" --el timestamp 1774550400000 2>&1 | head -1

sleep 1

echo "=== 1.5 添加前3天的时间戳: $THREE_DAYS_AGO ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "test" --el timestamp $THREE_DAYS_AGO 2>&1 | head -1

sleep 1

echo "=== 1.6 删除3月1日的历史时间戳 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DELETE_TIMESTAMP \
  --es name "test" --el since 1774550400000 --el until 1774636800000 2>&1 | head -1

sleep 1

echo "=== 1.7 减少计数 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DECREMENT_COUNTER \
  --es name "test" 2>&1 | head -1

echo ""
echo "=============================================="
echo "测试 2: 中文名称计数器"
echo "=============================================="

echo "=== 2.1 删除旧中文计数器（如存在）==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DELETE_COUNTER \
  --es name "喝水" 2>&1 | head -1

sleep 1

echo "=== 2.2 添加中文计数器 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.ADD_COUNTER \
  --es name "喝水" --es category "健康" 2>&1 | head -1

sleep 2

echo "=== 2.3 增加计数 5 次 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.INCREMENT_COUNTER \
  --es name "喝水" --ei count 5 2>&1 | head -1

sleep 1

echo "=== 2.4 添加前3天的时间戳: $THREE_DAYS_AGO ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "喝水" --el timestamp $THREE_DAYS_AGO 2>&1 | head -1

sleep 1

echo "=== 2.5 删除前3天的时间戳 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DELETE_TIMESTAMP \
  --es name "喝水" --el since $THREE_DAYS_AGO --el until 1774636800000 2>&1 | head -1

sleep 1

echo "=== 2.6 减少计数 2 次 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DECREMENT_COUNTER \
  --es name "喝水" 2>&1 | head -1
sleep 1
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DECREMENT_COUNTER \
  --es name "喝水" 2>&1 | head -1

# 截图验证中文计数器状态
sleep 2
echo ""
echo "=== 3. 截图验证 ==="
adb -s $DEVICE shell screencap -p /sdcard/screen.png
adb -s $DEVICE pull /sdcard/screen.png ./verify_screen.png 2>&1

echo ""
echo "=== 4. 导出数据 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.EXPORT_DATA 2>&1 | head -1

sleep 3

echo ""
echo "=== 5. 拉取导出文件 ==="
adb -s $DEVICE pull /sdcard/Download/bettercounter_export.csv ./ 2>&1

echo ""
echo "=== 导出的数据内容 ==="
cat bettercounter_export.csv

echo ""
echo "=== 验证结果 ==="

# 验证英文计数器
echo ""
echo "--- 英文计数器验证 ---"
if grep -q '"name":"test"' bettercounter_export.csv; then
    echo "✅ 英文计数器存在"
    RESULT_EN1="✅"
else
    echo "❌ 英文计数器不存在"
    RESULT_EN1="❌"
fi

# 验证中文计数器 - 通过 SharedPreferences 验证
echo ""
echo "--- 中文计数器验证 ---"
CN_CHECK=$(adb -s $DEVICE shell "run-as org.kde.bettercounter.debug cat shared_prefs/prefs.xml" 2>/dev/null | grep -c "喝水")
if [ "$CN_CHECK" -gt 0 ]; then
    echo "✅ 中文计数器已保存到数据库"
    RESULT_CN1="✅"
else
    echo "❌ 中文计数器未保存"
    RESULT_CN1="❌"
fi

# 检查中文计数器的历史记录是否被删除
THREE_DAYS_CHECK=$(adb -s $DEVICE shell "run-as org.kde.bettercounter.debug cat shared_prefs/prefs.xml" 2>/dev/null | grep -c "$THREE_DAYS_AGO")
if [ "$THREE_DAYS_CHECK" -gt 0 ]; then
    echo "❌ 中文计数器前3天数据应该已删除"
    RESULT_CN2="❌"
else
    echo "✅ 中文计数器前3天数据已正确删除"
    RESULT_CN2="✅"
fi

echo ""
echo "=== 截图验证 ==="
echo "请查看 verify_screen.png 确认中文计数器 '喝水' 显示正常"

echo ""
echo "=== 最终结果 ==="
echo "英文计数器: $RESULT_EN1"
echo "中文计数器: $RESULT_CN1 $RESULT_CN2"

if [ "$RESULT_EN1" = "✅" ] && [ "$RESULT_CN1" = "✅" ] && [ "$RESULT_CN2" = "✅" ]; then
    echo "🎉 所有测试通过！"
    exit 0
else
    echo "⚠️ 部分测试失败"
    exit 1
fi
