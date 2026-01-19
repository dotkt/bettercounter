# 功能：数据可视化与统计

*   **描述：** 提供计数器数据的图表展示和详细统计分析功能。
*   **功能边界：**
    *   **图表展示：**
        *   在主界面底部的 `BottomSheetBehavior` 中显示。
        *   通过 `ChartsAdapter` 和 `ChartHolder` 渲染不同时间间隔（日、周、月、年）的计数数据折线图。
        *   允许用户选择不同的时间间隔来查看历史趋势。
        *   图表数据通过 `ViewModel.getEntriesForRangeSortedByDate` 获取指定日期范围内的条目。
        *   图表会显示计数器的名称、当前区间计数和目标（如果设置）。
    *   **详细统计对话框：**
        *   通过点击计数器详情页的“统计”按钮触发。
        *   使用 `StatisticsDialogAdapter` 渲染一个包含详细统计信息的对话框，特别是年度日历视图。
        *   年度日历视图以图形方式显示一年中每天是否有计数记录（✅表示有记录，❓表示今天无记录，❌表示过去无记录）。
        *   统计数据通过 `ViewModel.getAllEntriesSortedByDate` 获取计数器的所有条目。
        *   还可能包含总计数、平均间隔、最新/最早记录等文本统计信息。
*   **关键文件：**
    *   `app/src/main/java/org/kde/bettercounter/ChartsAdapter.kt`
    *   `app/src/main/java/org/kde/bettercounter/ui/ChartHolder.kt`
    *   `app/src/main/java/org/kde/bettercounter/ui/StatisticsDialogAdapter.kt`
    *   `app/src/main/java/org/kde/bettercounter/ViewModel.kt` (特别是 `getEntriesForRangeSortedByDate`, `getAllEntriesSortedByDate`, `getCounterSummary` 方法)
    *   `app/src/main/res/layout/fragment_chart.xml`
    *   `app/src/main/res/layout/dialog_statistics.xml` (如果存在)
    *   `app/src/main/res/layout/fragment_week_statistics.xml`
    *   `app/src/main/res/layout/fragment_text_statistics.xml` (如果存在)
*   **测试:**
    *   **数据查询测试:**
        *   **日期范围查询:** 验证 `getEntriesForRangeSortedByDate` 是否能准确返回指定日期范围内的计数条目。
        *   **摘要计算:** 验证 `getCounterSummary` 返回的摘要信息（如总数、最近计数等）是否与实际数据一致。
