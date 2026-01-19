# 功能：数据导入/导出

*   **描述：** 允许用户将计数器数据导出为文件，或从文件中导入计数器数据。
*   **功能边界：**
    *   **数据导出：**
        *   通过主界面菜单中的“导出 CSV”选项触发。
        *   需要存储权限，并在 Android 10+ 上进行特殊权限处理。
        *   将所有计数器的元数据（名称、颜色、时间间隔、目标、类别）和所有条目时间戳打包成自定义 JSON 格式（也兼容旧的 CSV 格式）。
        *   文件通常保存到设备的“下载”目录中。
        *   提供导出进度和完成提示。
        *   核心逻辑位于 `ViewModel.exportAll()` 方法。
    *   **数据导入：**
        *   通过主界面菜单中的“导入 CSV”选项触发。
        *   使用系统文件选择器 (`OpenFileResultContract`) 让用户选择要导入的文件。
        *   能够解析新旧两种文件格式：
            *   **新格式（带 JSON 配置）：** `{"name":"名称","color":"#RRGGBB","colorName":"RED","interval":"DAILY","goal":5,"category":"类别"},[时间戳1,时间戳2,...]`
            *   **旧格式（纯 CSV）：** `name,timestamp1,timestamp2,...`
        *   导入时，如果存在同名计数器，则会删除现有计数器，并使用导入的数据重新创建。
        *   导入成功后，刷新所有计数器数据和 UI。
        *   核心逻辑位于 `ViewModel.importAll()` 方法，并依赖于 `parseImportLineWithJSON`、`parseJsonObject`、`parseJsonValue`、`getColorIntFromValue`、`createCounterColorFromInt`、`getColorIntForName` 和 `parseTimestamps` 等辅助方法。
        *   提供导入进度和完成/错误提示。
*   **关键文件：**
    *   `app/src/main/java/org/kde/bettercounter/ui/MainActivity.kt` (处理权限、文件选择和调用 `ViewModel`)
    *   `app/src/main/java/org/kde/bettercounter/ViewModel.kt` (特别是 `exportAll`, `importAll`, `hasDataToExport` 及其内部辅助方法 `intToRGBString`, `getClosestColorName`, `extractColorInt`, `parseImportLineWithJSON`, `parseJsonObject`, `parseJsonValue`, `parseTimestamps`, `createCounterColorFromInt`, `getColorIntForName`)
    *   `app/src/main/java/org/kde/bettercounter/boilerplate/CreateFileResultContract.kt` (用于文件导出)
    *   `app/src/main/java/org/kde/bettercounter/boilerplate/OpenFileResultContract.kt` (用于文件导入)
*   **测试:**
    *   **导入/导出单元测试:**
        *   **旧格式解析:** 验证 `parseImportLine` 能否正确解析不带元数据的旧版 CSV 格式。
        *   **新格式解析:** 验证 `parseImportLineWithJSON` 能否正确解析包含完整元数据的新版 JSON 格式。
        *   **标准计数器导入:** 验证标准计数器导入后，其名称、分类、颜色、计数等所有属性都正确无误。
        *   **动态计数器导入:** 验证动态计数器导入后，其类型、公式等专属属性都正确无误。
        *   **数据导出:** 验证 `exportAll` 生成的数据格式符合预期，并且所有元数据（包括动态计数器的公式）都已正确导出。
