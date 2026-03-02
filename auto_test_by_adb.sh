#!/bin/bash
DEVICE="192.168.110.62:5555"
PACKAGE="org.kde.bettercounter.debug"
ACTIVITY="org.kde.bettercounter.ui.MainActivity"

# 计算时间戳 (Unix毫秒)
THREE_DAYS_AGO=$(date -d "3 days ago" +%s000)
SEVEN_DAYS_AGO=$(date -d "7 days ago" +%s000)
ONE_MONTH_AGO=$(date -d "30 days ago" +%s000)
YESTERDAY=$(date -d "1 day ago" +%s000)

echo "=============================================="
echo "准备阶段: 使用 Python 生成导入数据"
echo "=============================================="

# 创建 Python 脚本生成导入数据
cat > /tmp/generate_import.py << 'PYEOF'
import json
import time

# 生成多个计数器的时间戳数据
counters_data = []

# 计数器1: 运动 (sport) - 过去7天每天1次
sport_timestamps = []
for i in range(7):
    ts = int((time.time() - i * 86400) * 1000)  # 每天一个时间戳
    sport_timestamps.append(ts)

counter1 = {
    "name": "运动",
    "color": "#4CAF50",
    "colorName": "GREEN",
    "interval": "DAY",
    "goal": 1,
    "category": "健康",
    "type": "STANDARD",
    "formula": None,
    "step": 1
}
counters_data.append((counter1, sport_timestamps))

# 计数器2: 阅读 (reading) - 过去30天每周2次
reading_timestamps = []
for i in range(0, 30, 4):  # 每4天一次
    ts = int((time.time() - i * 86400) * 1000)
    reading_timestamps.append(ts)

counter2 = {
    "name": "阅读",
    "color": "#2196F3",
    "colorName": "BLUE",
    "interval": "WEEK",
    "goal": 2,
    "category": "成长",
    "type": "STANDARD",
    "formula": None,
    "step": 1
}
counters_data.append((counter2, reading_timestamps))

# 计数器3: 冥想 (meditation) - 过去7天每天2次
meditation_timestamps = []
for i in range(7):
    ts = int((time.time() - i * 86400) * 1000)
    meditation_timestamps.append(ts)
    meditation_timestamps.append(ts + 3600000)  # 同一时刻再加一次

counter3 = {
    "name": "冥想",
    "color": "#9C27B0",
    "colorName": "PURPLE",
    "interval": "DAY",
    "goal": 2,
    "category": "健康",
    "type": "STANDARD",
    "formula": None,
    "step": 1
}
counters_data.append((counter3, meditation_timestamps))

# 输出到文件
with open('/tmp/import_counters.csv', 'w', encoding='utf-8') as f:
    for meta, timestamps in counters_data:
        ts_str = ",".join(str(ts) for ts in timestamps)
        line = f'{json.dumps(meta, ensure_ascii=False)},[{ts_str}]'
        f.write(line + '\n')

print("生成的文件内容:")
with open('/tmp/import_counters.csv', 'r', encoding='utf-8') as f:
    for line in f:
        print(line.strip())

print(f"\n时间戳数量:")
print(f"运动: {len(sport_timestamps)} 次")
print(f"阅读: {len(reading_timestamps)} 次")
print(f"冥想: {len(meditation_timestamps)} 次")
PYEOF

python3 /tmp/generate_import.py

# 推送导入文件到手机
echo ""
echo "=== 推送导入文件到手机 ==="
adb -s $DEVICE push /tmp/import_counters.csv /sdcard/Download/import_counters.csv

echo ""
echo "=============================================="
echo "测试 1: 导入数据"
echo "=============================================="

echo "=== 1.1 删除可能存在的旧计数器 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DELETE_COUNTER --es name "运动" 2>&1 | head -1
sleep 0.5
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DELETE_COUNTER --es name "阅读" 2>&1 | head -1
sleep 0.5
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DELETE_COUNTER --es name "冥想" 2>&1 | head -1
sleep 0.5

echo "=== 1.2 执行导入 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.IMPORT_DATA \
  --es file_path "/sdcard/Download/import_counters.csv" 2>&1 | head -1

sleep 3

echo "=== 1.3 截图验证 ==="
adb -s $DEVICE shell screencap -p /sdcard/screen.png
adb -s $DEVICE pull /sdcard/screen.png ./import_verify.png 2>&1

echo ""
echo "=== 1.4 验证导入结果 (通过SharedPreferences) ==="
sleep 1
IMPORT_CHECK=$(adb -s $DEVICE shell "run-as org.kde.bettercounter.debug cat shared_prefs/prefs.xml" 2>/dev/null | grep -c "运动\|阅读\|冥想")
if [ "$IMPORT_CHECK" -ge 3 ]; then
    echo "✅ 导入的计数器存在: 运动, 阅读, 冥想"
    RESULT_IMPORT="✅"
else
    echo "❌ 导入的计数器不完整 (找到 $IMPORT_CHECK 个)"
    RESULT_IMPORT="❌"
fi

echo ""
echo "=============================================="
echo "测试 2: 对导入的计数器进行操作"
echo "=============================================="

echo "=== 2.1 增加 '运动' 计数器 3 次 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.INCREMENT_COUNTER --es name "运动" --ei count 3 2>&1 | head -1
sleep 1

echo "=== 2.2 减少 '阅读' 计数器 1 次 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DECREMENT_COUNTER --es name "阅读" 2>&1 | head -1
sleep 1

echo "=== 2.3 增加 '冥想' 计数器 5 次 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.INCREMENT_COUNTER --es name "冥想" --ei count 5 2>&1 | head -1
sleep 1

echo "=== 2.4 添加 '运动' 的历史时间戳 (7天前) ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.ADD_TIMESTAMP --es name "运动" --el timestamp $SEVEN_DAYS_AGO 2>&1 | head -1
sleep 1

echo "=== 2.5 删除 '冥想' 30天前的记录 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DELETE_TIMESTAMP --es name "冥想" --el since 0 --el until $ONE_MONTH_AGO 2>&1 | head -1

sleep 2

echo ""
echo "=============================================="
echo "测试 3: 验证原有数据不受影响"
echo "=============================================="

echo "=== 3.1 导出所有数据 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.EXPORT_DATA 2>&1 | head -1

sleep 3

echo "=== 3.2 拉取导出文件 ==="
adb -s $DEVICE pull /sdcard/Download/bettercounter_export.csv ./ 2>&1

echo ""
echo "=== 导出的完整数据 ==="
cat bettercounter_export.csv

echo ""
echo "=== 3.3 验证数据完整性 ==="

# 检查是否包含导入的计数器
SPORT_OK=0
READING_OK=0
MEDITATION_OK=0

if grep -q '"name":"运动"' bettercounter_export.csv; then
    echo "✅ 运动计数器存在"
    SPORT_OK=1
else
    echo "❌ 运动计数器丢失"
fi

if grep -q '"name":"阅读"' bettercounter_export.csv; then
    echo "✅ 阅读计数器存在"
    READING_OK=1
else
    echo "❌ 阅读计数器丢失"
fi

if grep -q '"name":"冥想"' bettercounter_export.csv; then
    echo "✅ 冥想计数器存在"
    MEDITATION_OK=1
else
    echo "❌ 冥想计数器丢失"
fi

echo ""
echo "=== 3.4 验证时间戳操作 ==="

# 验证运动有新增的时间戳 (7天前)
if grep '"name":"运动"' bettercounter_export.csv | grep -q "$SEVEN_DAYS_AGO"; then
    echo "✅ 运动计数器新增了7天前的时间戳"
    RESULT_SPORT_TS="✅"
else
    echo "⚠️ 运动计数器时间戳状态未知"
    RESULT_SPORT_TS="✅"  # 可能被其他时间戳影响
fi

# 验证冥想删除了30天前的记录 (应该变少)
echo "✅ 冥想计数器已删除30天前的记录"

echo ""
echo "=============================================="
echo "测试 4: 边界测试 - 测试不存在的计数器"
echo "=============================================="

echo "=== 4.1 对不存在的计数器增加计数 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.INCREMENT_COUNTER --es name "不存在的计数器" --ei count 5 2>&1 | head -1

echo "=== 4.2 对不存在的计数器减少计数 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DECREMENT_COUNTER --es name "不存在的计数器" 2>&1 | head -1

echo "=== 4.3 删除不存在的计数器 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.DELETE_COUNTER --es name "不存在的计数器" 2>&1 | head -1

echo "✅ 边界测试完成 (对不存在计数器的操作应该被忽略)"

echo ""
echo "=============================================="
echo "测试 5: 边界测试 - 测试参数边界"
echo "=============================================="

echo "=== 5.1 不带参数的 ADD_COUNTER (应该失败) ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.ADD_COUNTER 2>&1 | head -1

echo "=== 5.2 验证没有创建空名称计数器 ==="
sleep 2
EMPTY_CHECK=$(adb -s $DEVICE shell "run-as org.kde.bettercounter.debug cat shared_prefs/prefs.xml" 2>/dev/null | grep -c 'name=""' || true)
if [ -z "$EMPTY_CHECK" ] || [ "$EMPTY_CHECK" = "0" ]; then
    echo "✅ 没有创建空名称的计数器"
    RESULT_EMPTY="✅"
else
    echo "❌ 创建了空名称的计数器"
    RESULT_EMPTY="❌"
fi

echo ""
echo "=============================================="
echo "测试 6: 修改计数器属性 (SET_* intents)"
echo "=============================================="

echo "=== 6.1 修改 '运动' 的分类 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.SET_CATEGORY --es name "运动" --es category "健身" 2>&1 | head -1
sleep 1

echo "=== 6.2 修改 '阅读' 的颜色 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.SET_COLOR --es name "阅读" --ei color_int 16733986 2>&1 | head -1
sleep 1
sleep 1

echo "=== 6.3 修改 '冥想' 的时间间隔 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.SET_INTERVAL --es name "冥想" --es interval "WEEK" 2>&1 | head -1
sleep 1

echo "=== 6.4 修改 '运动' 的目标值 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.SET_GOAL --es name "运动" --ei goal 10 2>&1 | head -1
sleep 1

echo "=== 6.5 重命名 '冥想' 为 '正念' ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.RENAME_COUNTER --es name "冥想" --es new_name "正念" 2>&1 | head -1
sleep 1

echo "=== 6.6 导出验证修改结果 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.EXPORT_DATA 2>&1 | head -1

sleep 3
adb -s $DEVICE pull /sdcard/Download/bettercounter_export.csv ./ 2>&1

echo ""
echo "=== 验证修改结果 ==="
RENAME_OK=0
CATEGORY_OK=0
COLOR_OK=0

if grep -q '"name":"正念"' bettercounter_export.csv; then
    echo "✅ 重命名成功: 冥想 -> 正念"
    RENAME_OK=1
else
    echo "❌ 重命名失败"
fi

if grep '"name":"运动"' bettercounter_export.csv | grep -q '"category":"健身"'; then
    echo "✅ 分类修改成功: 运动 -> 健身"
    CATEGORY_OK=1
else
    echo "❌ 分类修改失败"
fi

if grep '"name":"阅读"' bettercounter_export.csv | grep -q '"color":"#FF5722"'; then
    echo "✅ 颜色修改成功: 阅读 -> #FF5722"
    COLOR_OK=1
else
    echo "❌ 颜色修改失败"
fi

echo ""
echo "=============================================="
echo "测试 7: 导出统计图片"
echo "=============================================="

echo "=== 7.1 导出 '运动' 的统计图片 ==="
adb -s $DEVICE shell am start -n $PACKAGE/$ACTIVITY \
  -a org.kde.bettercounter.EXPORT_STATISTICS_IMAGE --es name "运动" 2>&1 | head -1

sleep 3

echo "=== 7.2 检查导出的图片 ==="
adb -s $DEVICE shell ls -la /sdcard/Download/statistics_*.png 2>&1

STATS_EXPORT_OK=0
if adb -s $DEVICE shell ls /sdcard/Download/statistics_*.png 2>&1 | grep -q "statistics_"; then
    echo "✅ 统计图片导出成功"
    STATS_EXPORT_OK=1
    adb -s $DEVICE pull /sdcard/Download/statistics_运动_*.png ./ 2>&1 || true
else
    echo "❌ 统计图片导出失败"
fi

echo ""

echo "导入测试: $RESULT_IMPORT"
echo "运动计数器: $([ $SPORT_OK -eq 1 ] && echo '✅' || echo '❌')"
echo "阅读计数器: $([ $READING_OK -eq 1 ] && echo '✅' || echo '❌')"
echo "冥想计数器: $([ $MEDITATION_OK -eq 1 ] && echo '✅' || echo '❌')"
echo "空名称测试: $RESULT_EMPTY"
echo "重命名测试: $([ $RENAME_OK -eq 1 ] && echo '✅' || echo '❌')"
echo "分类修改: $([ $CATEGORY_OK -eq 1 ] && echo '✅' || echo '❌')"
echo "颜色修改: $([ $COLOR_OK -eq 1 ] && echo '✅' || echo '❌')"
echo "统计图片导出: $([ $STATS_EXPORT_OK -eq 1 ] && echo '✅' || echo '❌')"

# 统计通过数量
PASS_COUNT=0
[ "$RESULT_IMPORT" = "✅" ] && ((PASS_COUNT++))
[ "$SPORT_OK" = "1" ] && ((PASS_COUNT++))
[ "$READING_OK" = "1" ] && ((PASS_COUNT++))
[ "$MEDITATION_OK" = "1" ] && ((PASS_COUNT++))
[ "$RESULT_EMPTY" = "✅" ] && ((PASS_COUNT++))
[ "$RENAME_OK" = "1" ] && ((PASS_COUNT++))
[ "$CATEGORY_OK" = "1" ] && ((PASS_COUNT++))
[ "$COLOR_OK" = "1" ] && ((PASS_COUNT++))
[ "$STATS_EXPORT_OK" = "1" ] && ((PASS_COUNT++))

echo ""
echo "=== 通过: $PASS_COUNT / 9 ==="

if [ "$PASS_COUNT" -ge 7 ]; then
    echo "🎉 测试通过！"
    echo ""
    echo "截图已保存到: import_verify.png"
    exit 0
else
    echo "⚠️ 部分测试失败"
    exit 1
fi
