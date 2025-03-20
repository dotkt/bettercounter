package org.kde.bettercounter.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.PendingIntent
import android.widget.RemoteViews
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.R
import org.kde.bettercounter.ui.MainActivity

/**
 * 大型计数器Widget，支持按分组显示计数器
 */
class LargeCounterWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // 每个Widget实例都要更新
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // 删除Widget时清除偏好设置
        for (appWidgetId in appWidgetIds) {
            LargeWidgetConfigureActivity.deletePref(appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            android.util.Log.d("LargeCounterWidget", "更新Widget: $appWidgetId")
            
            // 创建RemoteViews对象
            val views = RemoteViews(context.packageName, R.layout.large_counter_widget)
            
            // 获取应用ViewModel以访问数据
            val viewModel = (context.applicationContext as BetterApplication).viewModel
            
            // 获取为此Widget保存的分组ID
            val groupId = LargeWidgetConfigureActivity.loadGroupId(context, appWidgetId)
            android.util.Log.d("LargeCounterWidget", "使用分组ID: $groupId")
            
            // 过滤计数器列表，只显示所选分组的计数器
            val allCounters = viewModel.getCounterList()
            val groupCounters = allCounters.filter { viewModel.getCounterGroup(it) == groupId }
            android.util.Log.d("LargeCounterWidget", "分组内计数器数量: ${groupCounters.size}")
            
            // 在Widget上显示当前分组名称
            val groupName = viewModel.getGroups().find { it.id == groupId }?.name ?: context.getString(R.string.default_group)
            android.util.Log.d("LargeCounterWidget", "设置分组名称: $groupName")
            views.setTextViewText(R.id.widget_group_name, groupName)
            
            // 设置列表适配器
            val intent = Intent(context, LargeWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra("GROUP_ID", groupId) // 传递分组ID给RemoteViewsFactory
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            
            views.setRemoteAdapter(R.id.widget_list, intent)
            views.setEmptyView(R.id.widget_list, R.id.empty_view)
            
            // 设置点击事件处理
            val clickIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 
                appWidgetId, 
                clickIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, pendingIntent)
            
            // 添加分组切换按钮的点击事件
            val switchGroupIntent = Intent(context, GroupSelectorActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // 添加随机数确保Intent唯一
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME) + "&switch_group=" + System.currentTimeMillis())
            }
            
            val switchGroupPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 100, // 使用不同的请求码避免与其他PendingIntent冲突
                switchGroupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            views.setOnClickPendingIntent(R.id.switch_group_button, switchGroupPendingIntent)
            
            // 更新Widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
        }
    }
} 