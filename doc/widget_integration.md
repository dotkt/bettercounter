# 功能：小部件集成

*   **描述：** 允许用户将计数器添加到主屏幕作为小部件，以便快速查看和增量计数，并能够从小部件直接跳转到应用程序中的相应计数器详情。
*   **功能边界：**
    *   **小部件配置：**
        *   当用户添加新小部件时，启动 `WidgetConfigureActivity`。
        *   `WidgetConfigureActivity` 显示现有计数器列表，允许用户选择要与小部件关联的特定计数器。
        *   支持通过搜索文本和分类进行筛选。
        *   选择后，将 `appWidgetId` 与选定的计数器名称存储在共享偏好设置中。
    *   **小部件显示：**
        *   通过 `WidgetProvider` 渲染小部件 UI。
        *   显示计数器名称、当前计数和上次更新的相对时间。
        *   小部件背景颜色与计数器颜色一致。
        *   对于非每日计数的计数器，如果当天有记录，会显示一个“👍”图标。
    *   **小部件交互：**
        *   **增量计数：** 点击小部件背景区域会触发关联计数器的增量操作 (`ACTION_COUNT`)，并通过 `ViewModel.incrementCounterWithCallback` 更新数据，并刷新小部件 UI。
        *   **打开应用：** 点击小部件名称会打开 `MainActivity`，并导航到小部件关联的计数器详情页面 (`MainActivity.EXTRA_COUNTER_NAME`)。
    *   **智能更新：**
        *   `WidgetProvider` 实现了智能时间更新调度 (`scheduleSmartTimeUpdate`)，根据计数器上次更新的时间动态调整小部件的刷新频率（例如，最近更新的计数器更新更频繁）。
        *   使用 `AlarmManager` 来调度 `ACTION_UPDATE_TIME` 广播，以定期更新时间显示而无需完全刷新所有计数器数据。
    *   **小部件生命周期管理：**
        *   `onUpdate`：处理小部件的更新请求，刷新所有已配置的小部件。
        *   `onDeleted`：当小部件从主屏幕移除时，清除其相关的持久化数据。
        *   提供 `removeWidgets` 函数，用于当计数器被删除时，自动移除所有关联的小部件。
        *   提供 `forceRefreshWidgets` 函数，用于在数据变更后强制刷新所有小部件。
*   **关键文件：**
    *   `app/src/main/java/org/kde/bettercounter/ui/WidgetConfigureActivity.kt`
    *   `app/src/main/java/org/kde/bettercounter/ui/WidgetProvider.kt` (特别是 `onUpdate`, `onReceive`, `updateAppWidget`, `scheduleSmartTimeUpdate`, `formatRecentTime`)
    *   `app/src/main/res/xml/widget_info.xml`
    *   `app/src/main/res/layout/widget_configure.xml`
    *   `app/src/main/res/layout/widget.xml`
    *   `app/src/main/res/layout/widget_preview.xml`
    *   `MainActivity.kt` (通过 `handleIntent` 接收小部件点击)
    *   共享偏好设置文件（如 `org.kde.bettercounter.ui.WidgetProvider.xml`）用于持久化小部件与计数器的关联。
*   **测试:**
    *   **格式化逻辑测试 (单元测试):**
        *   **`formatRecentTime`:** 验证此函数能否根据不同的时间差，正确地格式化为预期的相对时间字符串（如：“刚刚”、“5分钟前”、“3小时前”、“昨天”）。
    *   **小部件 UI 和交互 (手动测试):**
        *   小部件的完整功能，包括 UI 显示和点击交互，依赖于 Android 系统环境，需要通过在设备或模拟器上进行手动测试来验证。
