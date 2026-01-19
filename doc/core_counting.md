# 功能：核心计数

*   **描述：** 计数器应用的基本功能，允许用户创建、增加、减少和管理计数器。减数功能主要用于修正用户的误触。
*   **功能边界：**
    *   **创建新计数器：**
        *   从主菜单触发。
        *   打开设置对话框，定义计数器的属性（名称、初始值、颜色等）。
        *   通过 `ViewModel` 将新计数器保存到数据库。
    *   **增加/减少计数器：**
        *   通过点击列表中的计数器项目上的加/减按钮触发。
        *   通过 `ViewModel` 更新数据库中计数器的值。
        *   支持预定义值（+1、+5、+10、-1、-5、-10）和自定义值。
    *   **编辑计数器：**
        *   通过选择一个计数器并点击“编辑”按钮触发。
        *   打开设置对话框修改计数器的属性。
    *   **重置计数器：**
        *   从“编辑”对话框中触发。
        *   将计数器的值重置为初始状态。
    *   **删除计数器：**
        *   从“编辑”对话框中触发。
        *   从数据库中删除计数器。
*   **关键文件：**
    *   `app/src/main/java/org/kde/bettercounter/ui/MainActivity.kt`
    *   `app/src/main/java/org/kde/bettercounter/ViewModel.kt`
    *   `app/src/main/java/org/kde/bettercounter/EntryListViewAdapter.kt`
    *   `app/src/main/java/org/kde/bettercounter/ui/EntryViewHolder.kt`
    *   `app/src/main/java/org/kde/bettercounter/ui/CounterSettingsDialogBuilder.kt`
    *   `app/src/main/res/layout/activity_main.xml`
    *   `app/src/main/res/layout/fragment_entry.xml`
*   **测试:**
    *   **ViewModel 测试:**
        *   **创建计数器:** 验证 `addCounter` 后，计数器是否成功创建并具有正确的初始值和元数据。
        *   **增量计数器:** 验证 `incrementCounter` 是否正确增加计数器的值。
        *   **编辑和重命名:** 验证 `editCounter` 能否正确修改计数器的元数据，包括名称。
        *   **重置计数器:** 验证 `resetCounter` 是否将计数器的值清零。
        *   **删除计数器:** 验证 `deleteCounter` 是否从系统中完全移除计数器。
