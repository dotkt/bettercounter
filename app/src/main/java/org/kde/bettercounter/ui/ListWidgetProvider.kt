package org.kde.bettercounter.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.kde.bettercounter.BuildConfig
import org.kde.bettercounter.R

class ListWidgetProvider : AppWidgetProvider() {
    
    companion object {
        fun updateListWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // 创建列表视图的 RemoteViews
            val views = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_list)

            // 设置列表的适配器
            val serviceIntent = Intent(context, WidgetListService::class.java)
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            
            // 设置列表的适配器
            views.setRemoteAdapter(R.id.widgetList, serviceIntent)

            // 设置列表项点击事件的模板
            val clickIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val clickPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.widgetList, clickPendingIntent)

            // 更新 widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widgetList)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateListWidget(context, appWidgetManager, appWidgetId)
        }
    }
} 