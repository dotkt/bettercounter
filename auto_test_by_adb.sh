#!/bin/bash
DEVICE="192.168.110.62:5555"
PACKAGE="org.kde.bettercounter.debug"
ACTIVITY="org.kde.bettercounter.ui.MainActivity"

# 计算前3天时间戳 (Unix毫秒)
THREE_DAYS_AGO=$(date -d "3 days ago" +%s000)

echo "=== 1. 删除旧计数器（如存在）==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DELETE_COUNTER \
  --es name "test" 2>&1 | head -1

echo "=== 2. 添加计数器 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.ADD_COUNTER \
  --es name "test" --es category "test" 2>&1 | head -1

echo "=== 3. 增加计数 3 次 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.INCREMENT_COUNTER \
  --es name "test" --ei count 3 2>&1 | head -1

echo "=== 4. 添加历史时间戳（固定日期: 2026-03-01）==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "test" --el timestamp 1774550400000 2>&1 | head -1

echo "=== 5. 添加前3天的时间戳: $THREE_DAYS_AGO ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.ADD_TIMESTAMP \
  --es name "test" --el timestamp $THREE_DAYS_AGO 2>&1 | head -1

echo "=== 6. 删除3月1日的历史时间戳 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DELETE_TIMESTAMP \
  --es name "test" --el since 1774550400000 --el until 1774636800000 2>&1 | head -1

echo "=== 7. 减少计数 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DECREMENT_COUNTER \
  --es name "test" 2>&1 | head -1

echo "=== 8. 导出数据 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.EXPORT_DATA 2>&1 | head -1

sleep 3

echo "=== 9. 拉取导出文件 ==="
adb -s $DEVICE pull /sdcard/Download/bettercounter_export.csv ./ 2>&1

echo ""
echo "=== 导出的数据内容 ==="
cat bettercounter_export.csv

echo ""
echo "=== 验证结果 ==="

# 验证1: 检查是否有前3天的数据
if grep -q "$THREE_DAYS_AGO" bettercounter_export.csv; then
    echo "✅ 测试通过: 前3天($THREE_DAYS_AGO)的数据存在"
    RESULT1="✅"
else
    echo "❌ 测试失败: 未找到前3天的数据"
    RESULT1="❌"
fi

# 验证2: 检查是否还有3月1日的数据 (应该已删除)
if grep -q "1774550400000" bettercounter_export.csv; then
    echo "❌ 测试失败: 3月1日的数据应该已被删除"
    RESULT2="❌"
else
    echo "✅ 测试通过: 3月1日的数据已正确删除"
    RESULT2="✅"
fi

echo ""
echo "=== 最终结果 ==="
echo "前3天数据存在: $RESULT1"
echo "3月1日数据已删除: $RESULT2"

if [ "$RESULT1" = "✅" ] && [ "$RESULT2" = "✅" ]; then
    echo "🎉 所有测试通过！"
    exit 0
else
    echo "⚠️ 部分测试失败"
    exit 1
fi
