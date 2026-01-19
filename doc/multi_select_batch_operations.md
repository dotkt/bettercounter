# 功能：多选和批量操作

*   **描述：** 允许用户同时选择多个计数器，并对这些选定的计数器执行批量操作，如更改分类或颜色。
*   **功能边界：**
    *   **进入/退出多选模式：**
        *   通过主界面菜单中的“多选模式”选项进入。
        *   进入多选模式后，会在顶部显示一个专用的多选工具栏 (`binding.multiSelectToolbar`)。
        *   点击多选工具栏上的“取消”按钮退出多选模式。
        *   退出多选模式时，会清除所有已选中的计数器，并隐藏多选工具栏。
    *   **计数器选择：**
        *   在多选模式下，每个计数器项目会显示一个复选框。
        *   用户可以点击复选框或计数器名称来切换其选中状态。
        *   多选工具栏会实时显示当前选中的计数器数量 (`binding.selectedCountText`)。
    *   **批量选择操作：**
        *   **全选：** 多选工具栏提供“全选”按钮，用于选中当前可见分类下的所有计数器。
        *   **反选：** 多选工具栏提供“反选”按钮，用于反转当前可见分类下计数器的选中状态。
    *   **批量修改分类：**
        *   点击多选工具栏上的“批量修改分类”按钮触发。
        *   弹出一个对话框，允许用户从现有分类中选择或输入一个新分类。
        *   确认后，将所有选定计数器的分类更新为新分类，并通过 `ViewModel.editCounterSameName` 持久化更改。
        *   操作完成后，显示结果提示并退出多选模式。
    *   **批量修改颜色：**
        *   点击多选工具栏上的“批量修改颜色”按钮触发。
        *   弹出一个颜色选择器对话框，允许用户选择一个新颜色。
        *   确认后，将所有选定计数器的颜色更新为新颜色，并通过 `ViewModel.editCounterSameName` 持久化更改。
        *   操作完成后，显示结果提示并退出多选模式。
*   **关键文件：**
    *   `app/src/main/java/org/kde/bettercounter/ui/MainActivity.kt` (处理多选模式的进入/退出、工具栏显示、批量操作对话框的显示和逻辑)
    *   `app/src/main/java/org/kde/bettercounter/EntryListViewAdapter.kt` (管理每个计数器项目的多选状态、选中集合、全选/反选逻辑)
    *   `app/src/main/java/org/kde/bettercounter/ui/EntryViewHolder.kt` (根据多选模式显示/隐藏复选框并处理选中状态)
    *   `app/src/main/java/org/kde/bettercounter/ViewModel.kt` (提供 `editCounterSameName` 用于批量更新计数器元数据)
    *   `app/src/main/res/layout/activity_main.xml` (包含多选工具栏 `multiSelectToolbar`)
*   **测试:**
    *   **批量编辑测试:**
        *   **`editCounterSameName`:** 验证调用此方法后，计数器的元数据（如颜色、分类）是否被正确地批量更新。
