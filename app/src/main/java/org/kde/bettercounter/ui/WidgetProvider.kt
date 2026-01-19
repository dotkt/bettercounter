package org.kde.bettercounter.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.lifecycle.Observer
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.BuildConfig
import org.kde.bettercounter.R
import org.kde.bettercounter.ViewModel
import org.kde.bettercounter.persistence.CounterSummary
import org.kde.bettercounter.persistence.Interval
import java.util.Date
import java.util.Calendar

private const val ACTION_COUNT = "org.kde.bettercounter.WidgetProvider.COUNT"
private const val ACTION_UPDATE_TIME = "org.kde.bettercounter.WidgetProvider.UPDATE_TIME"
private const val EXTRA_WIDGET_ID = "EXTRA_WIDGET_ID"

private const val TAG = "WidgetProvider"

class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d("DynamicCounterBug", "onUpdate called for widget IDs: ${appWidgetIds.joinToString()}")
        val viewModel = (context.applicationContext as BetterApplication).viewModel
        // When widgets are updated, we must ensure dynamic counters are recalculated.
        viewModel.recalculateDynamicCounters()
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, viewModel, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            deleteWidgetCounterNamePref(context, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("DynamicCounterBug", "onReceive received action: ${intent.action}")
        if (intent.action == ACTION_COUNT) {
            val appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                Log.e(TAG, "No widget id extra set")
                return
            }
            if (!existsWidgetCounterNamePref(context, appWidgetId)) {
                Log.e(TAG, "Counter doesn't exist")
                return
            }
            val counterName = loadWidgetCounterNamePref(context, appWidgetId)
            Log.d("DynamicCounterBug", "ACTION_COUNT for widget ID $appWidgetId, counter '$counterName'")
            val viewModel = (context.applicationContext as BetterApplication).viewModel
            viewModel.incrementCounterWithCallback(counterName) {
                if (!viewModel.getCounterSummary(counterName).hasObservers()) {
                    // The app was terminated and we got unsubscribed
                    Log.d(TAG, "CounterSummary has no observers")
                    updateAppWidget(context, viewModel, AppWidgetManager.getInstance(context), appWidgetId)
                }
                // Force all widgets to refresh to ensure dynamic counters are updated.
                forceRefreshWidgets(context)
            }
        } else if (intent.action == ACTION_UPDATE_TIME) {
            // 处理时间更新请求
            val appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && existsWidgetCounterNamePref(context, appWidgetId)) {
                val viewModel = (context.applicationContext as BetterApplication).viewModel
                updateAppWidgetTimeOnly(context, viewModel, AppWidgetManager.getInstance(context), appWidgetId)
            }
        } else if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            // 系统触发的更新，更新所有widget
            val viewModel = (context.applicationContext as BetterApplication).viewModel
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = getAllWidgetIds(context)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, viewModel, appWidgetManager, appWidgetId)
            }
        }
    }
}

fun getAllWidgetIds(context: Context): IntArray {
    return AppWidgetManager.getInstance(context).getAppWidgetIds(
        ComponentName(context, WidgetProvider::class.java)
    )
}

fun removeWidgets(context: Context, counterName: String) {
    val ids = getAllWidgetIds(context)
    val host = AppWidgetHost(context, 0)
    for (appWidgetId in ids) {
        if (counterName == loadWidgetCounterNamePref(context, appWidgetId)) {
            Log.d(TAG, "Deleting widget")
            // In Android 5 deleteAppWidgetId doesn't remove the widget but in Android 13 it does.
            host.deleteAppWidgetId(appWidgetId)
            deleteWidgetCounterNamePref(context, appWidgetId)
        }
    }
}

fun forceRefreshWidgets(context: Context) {
    Log.d("DynamicCounterBug", "forceRefreshWidgets called.")
    val widgetIds = getAllWidgetIds(context)
    if (widgetIds.isNotEmpty()) {
        Log.d(TAG, "Refreshing ${widgetIds.size} widgets")
        val intent = Intent(context, WidgetProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
        context.sendBroadcast(intent)
    } else {
        Log.d(TAG, "No widgets to refresh")
    }
}

internal fun updateAppWidget(
    context: Context,
    viewModel: ViewModel,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    if (!existsWidgetCounterNamePref(context, appWidgetId)) {
        Log.e(TAG, "Ignoring updateAppWidget for an unconfigured widget")
        return
    }

    val counterName = loadWidgetCounterNamePref(context, appWidgetId)

    val views = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget)

    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_COUNTER_NAME, counterName)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    
    val openAppPendingIntent = PendingIntent.getActivity(
        context, 
        appWidgetId,
        openAppIntent, 
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    views.setOnClickPendingIntent(R.id.widgetName, openAppPendingIntent)

    if (!viewModel.counterExists(counterName)) {
        Log.e(TAG, "The counter for this widget doesn't exist")
        views.setTextViewText(R.id.widgetCounter, "error")
        views.setTextViewText(R.id.widgetTime, "not found")
        appWidgetManager.updateAppWidget(appWidgetId, views)
        return
    }

    var prevCounterName = counterName
    viewModel.getCounterSummary(counterName).observeForever(object : Observer<CounterSummary> {
        override fun onChanged(value: CounterSummary) {
            Log.d("DynamicCounterBug", "Observer onChanged for widget ID $appWidgetId, counter '${value.name}' (type: ${value.type}), new count: ${value.lastIntervalCount}")
            if (!existsWidgetCounterNamePref(context, appWidgetId)) {
                Log.d("DynamicCounterBug", "Widget ID $appWidgetId no longer configured, removing observer.")
                viewModel.getCounterSummary(value.name).removeObserver(this)
                cancelTimeUpdateAlarm(context, appWidgetId)
                return
            }
            if (prevCounterName != value.name) {
                saveWidgetCounterNamePref(context, appWidgetId, value.name)
                prevCounterName = value.name
            }

            // Set click behavior based on counter type
            if (value.type == org.kde.bettercounter.persistence.CounterType.STANDARD) {
                // Standard counter: background click increments the counter.
                val countIntent = Intent(context, WidgetProvider::class.java).apply {
                    action = ACTION_COUNT
                    putExtra(EXTRA_WIDGET_ID, appWidgetId)
                }
                val countPendingIntent = PendingIntent.getBroadcast(
                    context, appWidgetId, countIntent, 
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widgetBackground, countPendingIntent)
            } else {
                // Dynamic counter: background click opens the app (same as title click).
                val dynamicOpenAppIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_COUNTER_NAME, value.name)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val dynamicOpenAppPendingIntent = PendingIntent.getActivity(
                    context, 
                    appWidgetId, 
                    dynamicOpenAppIntent, 
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widgetBackground, dynamicOpenAppPendingIntent)
            }

            views.setInt(R.id.widgetBackground, "setBackgroundColor", value.color.colorInt)
            views.setTextViewText(R.id.widgetName, value.name)
            views.setTextViewText(R.id.widgetCounter, value.getFormattedCount(forWidget = true))
            
            // For dynamic counters, the time of the last "entry" is meaningless.
            val date = if (value.type == org.kde.bettercounter.persistence.CounterType.STANDARD) value.mostRecent else null

            if (date != null) {
                val formattedDate = formatRecentTime(date, context)
                views.setTextViewText(R.id.widgetTime, formattedDate)
                
                if (value.interval != Interval.DAY) {
                    val now = Calendar.getInstance()
                    val mostRecentDate = Calendar.getInstance().apply { time = date }
                    val hasTodayEntry = isSameDay(now, mostRecentDate)
                    if (hasTodayEntry) {
                        views.setViewVisibility(R.id.widgetCheckmark, android.view.View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.widgetCheckmark, android.view.View.GONE)
                    }
                } else {
                    views.setViewVisibility(R.id.widgetCheckmark, android.view.View.GONE)
                }
            } else {
                views.setTextViewText(R.id.widgetTime, if (value.type == org.kde.bettercounter.persistence.CounterType.DYNAMIC) "" else context.getString(R.string.never))
                views.setViewVisibility(R.id.widgetCheckmark, android.view.View.GONE)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
            
            scheduleSmartTimeUpdate(context, appWidgetId, date)
        }
    })
}

/**
 * 格式化最近完成时间的显示格式
 * 与主界面的 formatRelativeTime 逻辑保持一致
 * 小于60秒：显示"刚刚"
 * 大于等于60秒但小于1小时：显示几分钟前
 * 1天以内：显示几小时前
 * 超过1天但不超过30天：显示几天前
 * 超过30天但不超过12个月：显示几月前
 * 超过12个月：显示几年前
 */
private fun formatRecentTime(date: Date, context: Context): String {
    val now = Calendar.getInstance()
    val targetDate = Calendar.getInstance().apply { time = date }
    
    val diffInMillis = now.timeInMillis - targetDate.timeInMillis
    val diffInSeconds = diffInMillis / 1000
    val diffInMinutes = diffInMillis / (60 * 1000)
    val diffInHours = diffInMillis / (60 * 60 * 1000)
    val diffInDays = diffInMillis / (24 * 60 * 60 * 1000)
    
    return when {
        diffInSeconds < 60 -> {
            // 小于60秒：显示"刚刚"
            "刚刚"
        }
        diffInHours < 1 -> {
            // 大于等于60秒但小于1小时：显示几分钟前
            "${diffInMinutes}分钟前"
        }
        diffInHours < 24 -> {
            // 1天以内：显示几小时前
            "${diffInHours}小时前"
        }
        diffInDays < 30 -> {
            // 超过1天但不超过30天：显示几天前
            "${diffInDays}天前"
        }
        else -> {
            // 计算月数和年数
            val years = now.get(Calendar.YEAR) - targetDate.get(Calendar.YEAR)
            val months = years * 12 + (now.get(Calendar.MONTH) - targetDate.get(Calendar.MONTH))
            
            when {
                months >= 12 -> {
                    // 超过12个月：显示几年前
                    "${years}年前"
                }
                else -> {
                    // 超过30天但不超过12个月：显示几月前
                    "${months}月前"
                }
            }
        }
    }
}

/**
 * 检查两个日期是否是同一天
 */
private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

/**
 * 检查目标日期是否是昨天
 */
private fun isYesterday(now: Calendar, targetDate: Calendar): Boolean {
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now.timeInMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }
    return isSameDay(yesterday, targetDate)
}

/**
 * 只更新widget的时间显示（不更新其他内容）
 */
private fun updateAppWidgetTimeOnly(
    context: Context,
    viewModel: ViewModel,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    if (!existsWidgetCounterNamePref(context, appWidgetId)) {
        return
    }
    
    val counterName = loadWidgetCounterNamePref(context, appWidgetId)
    if (!viewModel.counterExists(counterName)) {
        return
    }
    
    val counterSummary = viewModel.getCounterSummary(counterName).value
    if (counterSummary == null) {
        return
    }
    
    val views = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget)
    val date = counterSummary.mostRecent
    
    if (date != null) {
        val formattedDate = formatRecentTime(date, context)
        views.setTextViewText(R.id.widgetTime, formattedDate)
        
        // 对所有非DAILY类型的计数器，判断今天是否有记录，如果有则显示👍
        if (counterSummary.interval != Interval.DAY) {
            val now = Calendar.getInstance()
            val mostRecentDate = Calendar.getInstance().apply { time = date }
            val hasTodayEntry = isSameDay(now, mostRecentDate)
            if (hasTodayEntry) {
                views.setViewVisibility(R.id.widgetCheckmark, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widgetCheckmark, android.view.View.GONE)
            }
        } else {
            views.setViewVisibility(R.id.widgetCheckmark, android.view.View.GONE)
        }
    } else {
        views.setTextViewText(R.id.widgetTime, context.getString(R.string.never))
        views.setViewVisibility(R.id.widgetCheckmark, android.view.View.GONE)
    }
    
    appWidgetManager.updateAppWidget(appWidgetId, views)
    
    // 继续调度下一次更新
    scheduleSmartTimeUpdate(context, appWidgetId, date)
}

/**
 * 根据时间状态智能调度下一次更新
 * - "刚刚"状态（<60秒）：每30秒更新一次
 * - "X分钟前"状态（<1小时）：每1分钟更新一次
 * - "X小时前"状态（<24小时）：每5分钟更新一次
 * - "X天前"状态（<30天）：每30分钟更新一次
 * - 其他：每小时更新一次
 */
private fun scheduleSmartTimeUpdate(context: Context, appWidgetId: Int, date: Date?) {
    if (date == null) {
        // 如果没有日期，取消之前的定时器
        cancelTimeUpdateAlarm(context, appWidgetId)
        return
    }
    
    // 先取消之前的定时器，避免多个定时器同时运行
    cancelTimeUpdateAlarm(context, appWidgetId)
    
    val now = Calendar.getInstance()
    val targetDate = Calendar.getInstance().apply { time = date }
    val diffInMillis = now.timeInMillis - targetDate.timeInMillis
    val diffInSeconds = diffInMillis / 1000
    val diffInHours = diffInMillis / (60 * 60 * 1000)
    val diffInDays = diffInMillis / (24 * 60 * 60 * 1000)
    
    val updateIntervalMillis = when {
        diffInSeconds < 60 -> 30 * 1000L  // 30秒
        diffInHours < 1 -> 60 * 1000L  // 1分钟
        diffInHours < 24 -> 5 * 60 * 1000L  // 5分钟
        diffInDays < 30 -> 30 * 60 * 1000L  // 30分钟
        else -> 60 * 60 * 1000L  // 1小时
    }
    
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val updateIntent = Intent(context, WidgetProvider::class.java).apply {
        action = ACTION_UPDATE_TIME
        putExtra(EXTRA_WIDGET_ID, appWidgetId)
    }
    val updatePendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId,
        updateIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    val triggerTime = System.currentTimeMillis() + updateIntervalMillis
    
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                updatePendingIntent
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, updatePendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, updatePendingIntent)
        }
        Log.d(TAG, "Scheduled time update for widget $appWidgetId in ${updateIntervalMillis / 1000} seconds")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to schedule time update: ${e.message}", e)
    }
}

/**
 * 取消widget的时间更新定时器
 */
private fun cancelTimeUpdateAlarm(context: Context, appWidgetId: Int) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val updateIntent = Intent(context, WidgetProvider::class.java).apply {
        action = ACTION_UPDATE_TIME
        putExtra(EXTRA_WIDGET_ID, appWidgetId)
    }
    val updatePendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId,
        updateIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(updatePendingIntent)
    Log.d(TAG, "Cancelled time update for widget $appWidgetId")
}
