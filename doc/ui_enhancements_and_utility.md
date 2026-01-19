# 功能：用户界面增强和实用工具

*   **描述：** 包含了各种提升用户体验的界面优化、交互改进和实用功能。
*   **功能边界：**
    *   **键盘管理：**
        *   通过 `dispatchTouchEvent` 拦截触摸事件，实现点击屏幕非输入区域时自动隐藏软键盘的逻辑。
        *   包含 `findViewAtScreenCoordinates`、`findViewAt` 和 `isButton` 等辅助方法来识别触摸的视图类型。
        *   `hideKeyboard()` 方法负责实际隐藏键盘并清除输入框焦点。
    *   **搜索功能：**
        *   主界面提供搜索框 (`searchEditText`) 和搜索图标 (`searchIconButton`)。
        *   用户输入文本时，实时显示搜索结果列表 (`searchResultsRecyclerView`)，隐藏主分类视图。
        *   搜索结果支持点击跳转到对应的计数器，并定位到其分类页面。
        *   提供清除搜索文本和收起搜索框的按钮。
    *   **分类导航：**
        *   主界面顶部提供可滚动的分类导航栏 (`categoryContainer`)。
        *   动态生成每个分类的按钮，并高亮显示当前选中的分类。
        *   点击分类按钮可切换 `ViewPager` 页面，显示对应分类下的计数器列表。
    *   **UI 布局和动画：**
        *   `updateViewPagerPadding()` 动态调整 `ViewPager` 和搜索结果 `RecyclerView` 的填充，以适应顶部工具栏（如多选工具栏）的显示/隐藏，防止内容被遮挡。
        *   `flashCounter(view: View)` 提供计数器项的闪烁动画，用于高亮显示特定计数器。
    *   **后台数据刷新：**
        *   `startRefreshEveryMinuteBoundary()` 启动一个后台协程，每分钟检查一次日期变化，如果日期改变，则调用 `viewModel.refreshAllObservers()` 刷新所有计数器数据和相关 UI（例如，计数器的相对时间显示）。
    *   **应用信息（关于对话框）：**
        *   通过主菜单的“关于”选项，显示一个对话框，包含应用的当前版本号和 Git 提交哈希。
    *   **清除所有数据：**
        *   通过主菜单的“清除所有数据”选项，提供一个确认对话框，允许用户删除所有计数器及其数据。
    *   **教程提示：**
        *   集成 `SimpleTooltip` 库，在用户首次进行某些操作时显示引导性教程提示（例如，拖放、选择日期、更改图表间隔）。
    *   **声音反馈：**
        *   `ViewModel.playDingSound()` 在计数器增量时播放一个“叮”的声音，提供听觉反馈。
*   **关键文件：**
    *   `app/src/main/java/org/kde/bettercounter/ui/MainActivity.kt` (包含大部分 UI 增强和实用工具方法的实现)
    *   `app/src/main/java/org/kde/bettercounter/ViewModel.kt` (提供 `playDingSound`, `refreshAllObservers`, `isTutorialShown`, `setTutorialShown` 等方法)
    *   `app/src/main/java/org/kde/bettercounter/EntryListViewAdapter.kt` (处理拖放教程)
    *   `app/src/main/java/org/kde/bettercounter/ui/EntryViewHolder.kt` (处理日期选择教程)
    *   `app/src/main/java/org/kde/bettercounter/ui/ChartHolder.kt` (处理图表区间更改教程)
    *   `app/src/main/res/layout/activity_main.xml` (包含搜索布局、分类导航栏等 UI 元素)
    *   `app/src/main/res/menu/main.xml` (定义菜单项，包括“关于”和“清除所有数据”)
*   **测试:**
    *   **数据清除测试:**
        *   **`clearAllData`:** 验证调用此方法后，所有计数器的条目是否都已从数据库中被清除。
