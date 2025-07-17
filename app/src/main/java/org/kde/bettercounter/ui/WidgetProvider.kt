package org.kde.bettercounter.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.lifecycle.Observer
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.BuildConfig
import org.kde.bettercounter.R
import org.kde.bettercounter.ViewModel
import org.kde.bettercounter.persistence.CounterSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

private const val ACTION_COUNT = "org.kde.bettercounter.WidgetProvider.COUNT"
private const val EXTRA_WIDGET_ID = "EXTRA_WIDGET_ID"

private const val TAG = "WidgetProvider"

class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val viewModel = (context.applicationContext as BetterApplication).viewModel
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
        Log.d(TAG, "onReceive " + intent.action)
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
            val viewModel = (context.applicationContext as BetterApplication).viewModel
            viewModel.incrementCounterWithCallback(counterName) {
                if (!viewModel.getCounterSummary(counterName).hasObservers()) {
                    // The app was terminated and we got unsubscribed
                    Log.d(TAG, "CounterSummary has no observers")
                    updateAppWidget(context, viewModel, AppWidgetManager.getInstance(context), appWidgetId)
                }
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
        // This gets called right after placing the widget even if it hasn't been configured yet.
        // In that case we can't do anything. This is useful for reconfigurable widgets, which don't
        // require an initial configuration dialog. Our widget isn't reconfigurable though (because
        // I didn't find a way to stop observing the previous livedata).
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
        //val host = AppWidgetHost(context, 0)
        //host.deleteAppWidgetId(appWidgetId)
        views.setTextViewText(R.id.widgetCounter, "error")
        views.setTextViewText(R.id.widgetTime, "not found")
        appWidgetManager.updateAppWidget(appWidgetId, views)
        return
    }

    val countIntent = Intent(context, WidgetProvider::class.java)
    countIntent.action = ACTION_COUNT
    countIntent.putExtra(EXTRA_WIDGET_ID, appWidgetId)
    // We pass appWidgetId as requestCode even if it's not used to force the creation a new PendingIntent
    // instead of reusing an existing one, which is what happens if only the "extras" field differs.
    // Docs: https://developer.android.com/reference/android/app/PendingIntent.html
    val countPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, countIntent, PendingIntent.FLAG_IMMUTABLE)
    views.setOnClickPendingIntent(R.id.widgetBackground, countPendingIntent)

    var prevCounterName = counterName
    // observeForever means it's not attached to any lifecycle so we need to call removeObserver manually
    viewModel.getCounterSummary(counterName).observeForever(object : Observer<CounterSummary> {
        override fun onChanged(value: CounterSummary) {
            if (!existsWidgetCounterNamePref(context, appWidgetId)) {
                // Prevent leaking the observer once the widget has been deleted by deleting it here
                viewModel.getCounterSummary(value.name).removeObserver(this)
                return
            }
            if (prevCounterName != value.name) {
                // Counter is being renamed, replace the name stored in the sharedpref
                saveWidgetCounterNamePref(context, appWidgetId, value.name)
                prevCounterName = value.name
            }

            views.setInt(R.id.widgetBackground, "setBackgroundColor", value.color.colorInt)
            views.setTextViewText(R.id.widgetName, value.name)
            views.setTextViewText(R.id.widgetCounter, value.getFormattedCount(forWidget = true))
            val date = value.mostRecent
            if (date != null) {
                val formattedDate = formatRecentTime(date, context)
                views.setTextViewText(R.id.widgetTime, formattedDate)
            } else {
                views.setTextViewText(R.id.widgetTime, context.getString(R.string.never))
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    })
}

/**
 * 格式化最近完成时间的显示格式
 * 如果是1分钟内完成的，就显示刚刚
 * 如果是10分钟内完成的，就显示几分钟以前
 * 如果是今天内完成的，就显示 今H:M:S
 * 如果是昨天完成的，就显示 昨H:M
 * 如果是一个月内完成的 就显示 多少天之前
 * 如果超过一个月才完成，就显示Y:M:D 其中年份比如225示25即可。
 */
private fun formatRecentTime(date: Date, context: Context): String {
    val now = Calendar.getInstance()
    val targetDate = Calendar.getInstance().apply { time = date }
    
    val diffInMillis = now.timeInMillis - targetDate.timeInMillis
    val diffInMinutes = diffInMillis / (60 * 1000)
    val diffInHours = diffInMillis / (60 * 60 * 1000)
    val diffInDays = diffInMillis / (24 * 60 * 60 * 1000)
    return when {
        diffInMinutes < 1 -> "刚刚"
        diffInMinutes < 10 -> "${diffInMinutes}分钟前"
        isSameDay(now, targetDate) -> {
            val timeFormat = SimpleDateFormat("H:mm:ss", Locale.getDefault())
            "今${timeFormat.format(date)}"
        }
        isYesterday(now, targetDate) -> {
            val timeFormat = SimpleDateFormat("H:mm", Locale.getDefault())
            "昨${timeFormat.format(date)}"
        }
        diffInDays < 30 -> "${diffInDays}天前"
        else -> {
            val yearFormat = SimpleDateFormat("yy", Locale.getDefault())
            val monthFormat = SimpleDateFormat("M", Locale.getDefault())
            val dayFormat = SimpleDateFormat("d", Locale.getDefault())
            "${yearFormat.format(date)}/${monthFormat.format(date)}/${dayFormat.format(date)}"
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
