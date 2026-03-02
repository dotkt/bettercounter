package org.kde.bettercounter.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.BuildConfig
import org.kde.bettercounter.R
import org.kde.bettercounter.ViewModel

private const val ACTION_CATEGORY_COUNT = "org.kde.bettercounter.CategoryWidgetProvider.COUNT"
private const val ACTION_CATEGORY_REFRESH = "org.kde.bettercounter.CategoryWidgetProvider.REFRESH"
private const val ACTION_CATEGORY_MESSAGE_UPDATE = "org.kde.bettercounter.CategoryWidgetProvider.MESSAGE_UPDATE"

private const val TAG = "CategoryWidgetProvider"

private val ENCOURAGING_MESSAGES = listOf(
    "你做出了什么取舍？",
    "能力决定自由",
    "运用知识力量",
    "实践摆脱虚空",
    "未来就在脚下",
    "行动才会改变",
    "无悔奔赴宇宙",
    "留下我的故事"
)

private const val WHITE_COLOR = 0xFFFFFFFF.toInt()
private const val BLACK_COLOR = 0xFF000000.toInt()

class CategoryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        val viewModel = (context.applicationContext as BetterApplication).viewModel
        viewModel.recalculateDynamicCounters()
        
        for (appWidgetId in appWidgetIds) {
            updateCategoryWidget(context, viewModel, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val host = AppWidgetHost(context, 0)
        for (appWidgetId in appWidgetIds) {
            host.deleteAppWidgetId(appWidgetId)
            deleteWidgetCategoryPref(context, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive action: ${intent.action}")
        
        if (intent.action == ACTION_CATEGORY_COUNT) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val counterName = intent.getStringExtra("counter_name") ?: return
            
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                Log.e(TAG, "No widget id extra set")
                return
            }
            
            val viewModel = (context.applicationContext as BetterApplication).viewModel
            viewModel.incrementCounterWithCallback(counterName, 1) {
                forceRefreshCategoryWidgets(context)
            }
        } else if (intent.action == ACTION_CATEGORY_REFRESH) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val viewModel = (context.applicationContext as BetterApplication).viewModel
                updateCategoryWidget(context, viewModel, AppWidgetManager.getInstance(context), appWidgetId)
            }
        } else if (intent.action == ACTION_CATEGORY_MESSAGE_UPDATE) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val viewModel = (context.applicationContext as BetterApplication).viewModel
                updateCategoryWidgetMessageOnly(context, appWidgetId)
            }
        }
    }
}

fun getAllCategoryWidgetIds(context: Context): IntArray {
    return AppWidgetManager.getInstance(context).getAppWidgetIds(
        ComponentName(context, CategoryWidgetProvider::class.java)
    )
}

fun forceRefreshCategoryWidgets(context: Context) {
    Log.d(TAG, "forceRefreshCategoryWidgets called")
    val widgetIds = getAllCategoryWidgetIds(context)
    if (widgetIds.isNotEmpty()) {
        val intent = Intent(context, CategoryWidgetProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
        context.sendBroadcast(intent)
    }
}

internal fun updateCategoryWidget(
    context: Context,
    viewModel: ViewModel,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    if (!existsWidgetCategoryPref(context, appWidgetId)) {
        Log.e(TAG, "Ignoring updateCategoryWidget for an unconfigured widget")
        return
    }

    val category = loadWidgetCategoryPref(context, appWidgetId)

    val views = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_category)
    views.setTextViewText(R.id.widgetCategoryTitle, category)

    val countersInCategory = viewModel.getCounterList().filter { name ->
        viewModel.getCounterCategory(name) == category
    }

    if (countersInCategory.isEmpty()) {
        views.setViewVisibility(R.id.widgetCategoryListView, android.view.View.GONE)
        views.setViewVisibility(R.id.widgetCategoryEmpty, android.view.View.VISIBLE)
        
        val randomMessage = ENCOURAGING_MESSAGES.random()
        views.setTextViewText(R.id.widgetCategoryEmpty, randomMessage)
        views.setTextColor(R.id.widgetCategoryEmpty, WHITE_COLOR)
        views.setFloat(R.id.widgetCategoryEmpty, "setTextSize", 36f)
        
        scheduleMessageUpdate(context, appWidgetId)
    } else {
        val countersWithUnmetGoal = countersInCategory.filter { name ->
            val summary = viewModel.getCounterSummaryValue(name)
            summary == null || summary.goal <= 0 || summary.lastIntervalCount < summary.goal
        }
        
        if (countersWithUnmetGoal.isEmpty()) {
            views.setViewVisibility(R.id.widgetCategoryListView, android.view.View.GONE)
            views.setViewVisibility(R.id.widgetCategoryEmpty, android.view.View.VISIBLE)
            
            val randomMessage = ENCOURAGING_MESSAGES.random()
            views.setTextViewText(R.id.widgetCategoryEmpty, randomMessage)
            views.setTextColor(R.id.widgetCategoryEmpty, WHITE_COLOR)
            views.setFloat(R.id.widgetCategoryEmpty, "setTextSize", 36f)
            
            scheduleMessageUpdate(context, appWidgetId)
        } else {
            views.setViewVisibility(R.id.widgetCategoryListView, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widgetCategoryEmpty, android.view.View.GONE)
            
            // Use black background and white text for counters
            views.setInt(R.id.widgetCategoryBackground, "setBackgroundColor", BLACK_COLOR)
            views.setTextColor(R.id.widgetCategoryTitle, WHITE_COLOR)
            
            val serviceIntent = Intent(context, CategoryWidgetRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra("category", category)
            }
            views.setRemoteAdapter(R.id.widgetCategoryListView, serviceIntent)
            
            val templateIntent = Intent(context, CategoryWidgetProvider::class.java).apply {
                action = ACTION_CATEGORY_COUNT
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val templatePendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                templateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widgetCategoryListView, templatePendingIntent)
            
            views.setEmptyView(R.id.widgetCategoryListView, R.id.widgetCategoryEmpty)
        }
    }

    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_CATEGORY, category)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val openAppPendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId,
        openAppIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    views.setOnClickPendingIntent(R.id.widgetCategoryTitle, openAppPendingIntent)

    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widgetCategoryListView)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

private fun scheduleMessageUpdate(context: Context, appWidgetId: Int) {
    cancelMessageUpdate(context, appWidgetId)
    
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val updateIntent = Intent(context, CategoryWidgetProvider::class.java).apply {
        action = ACTION_CATEGORY_MESSAGE_UPDATE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
    val updatePendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId + 10000,
        updateIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    val triggerTime = System.currentTimeMillis() + 10000 // 10 seconds
    
    try {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            updatePendingIntent
        )
        Log.d(TAG, "Scheduled message update for widget $appWidgetId in 10 seconds")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to schedule message update: ${e.message}", e)
    }
}

private fun cancelMessageUpdate(context: Context, appWidgetId: Int) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val updateIntent = Intent(context, CategoryWidgetProvider::class.java).apply {
        action = ACTION_CATEGORY_MESSAGE_UPDATE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
    val updatePendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId + 10000,
        updateIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(updatePendingIntent)
}

private fun updateCategoryWidgetMessageOnly(context: Context, appWidgetId: Int) {
    if (!existsWidgetCategoryPref(context, appWidgetId)) {
        return
    }
    
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val category = loadWidgetCategoryPref(context, appWidgetId)
    val viewModel = (context.applicationContext as BetterApplication).viewModel
    
    val countersInCategory = viewModel.getCounterList().filter { name ->
        viewModel.getCounterCategory(name) == category
    }
    
    if (countersInCategory.isEmpty()) {
        val views = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_category)
        
        val randomMessage = ENCOURAGING_MESSAGES.random()
        views.setTextViewText(R.id.widgetCategoryEmpty, randomMessage)
        views.setTextColor(R.id.widgetCategoryEmpty, WHITE_COLOR)
        views.setFloat(R.id.widgetCategoryEmpty, "setTextSize", 36f)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
        
        scheduleMessageUpdate(context, appWidgetId)
    } else {
        val countersWithUnmetGoal = countersInCategory.filter { name ->
            val summary = viewModel.getCounterSummaryValue(name)
            summary == null || summary.goal <= 0 || summary.lastIntervalCount < summary.goal
        }
        
        if (countersWithUnmetGoal.isEmpty()) {
            val views = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_category)
            
            val randomMessage = ENCOURAGING_MESSAGES.random()
            views.setTextViewText(R.id.widgetCategoryEmpty, randomMessage)
            views.setTextColor(R.id.widgetCategoryEmpty, WHITE_COLOR)
            views.setFloat(R.id.widgetCategoryEmpty, "setTextSize", 36f)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
            
            scheduleMessageUpdate(context, appWidgetId)
        } else {
            val views = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_category)
            
            // Use black background and white text for counters
            views.setInt(R.id.widgetCategoryBackground, "setBackgroundColor", BLACK_COLOR)
            views.setTextColor(R.id.widgetCategoryTitle, WHITE_COLOR)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
            
            cancelMessageUpdate(context, appWidgetId)
        }
    }
}
