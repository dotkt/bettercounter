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
