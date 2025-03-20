package org.kde.bettercounter.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import org.kde.bettercounter.R

/**
 * 负责管理所有Widget的更新
 */
object WidgetUpdateManager {
    // 更新所有Widget
    fun updateAllWidgets(context: Context) {
        // 更新LargeCounterWidget
        val largeWidgetManager = AppWidgetManager.getInstance(context)
        val largeWidgetIds = largeWidgetManager.getAppWidgetIds(
            ComponentName(context, LargeCounterWidget::class.java)
        )
        largeWidgetManager.notifyAppWidgetViewDataChanged(largeWidgetIds, R.id.widget_list)
        
        // 同时也刷新默认的WidgetProvider
        forceRefreshDefaultWidgets(context)
    }
    
    // 刷新默认Widget
    private fun forceRefreshDefaultWidgets(context: Context) {
        try {
            // 调用现有的WidgetProvider刷新方法
            val widgetProviderClass = Class.forName("org.kde.bettercounter.ui.WidgetProvider")
            val refreshMethod = widgetProviderClass.getDeclaredMethod("forceRefreshWidgets", Context::class.java)
            refreshMethod.invoke(null, context)
        } catch (e: Exception) {
            android.util.Log.e("WidgetUpdateManager", "刷新默认Widget失败: ${e.message}")
        }
    }
} 