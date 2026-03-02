package org.kde.bettercounter.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.R

private const val TAG = "CategoryWidgetRVS"

class CategoryWidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CategoryWidgetRemoteViewsFactory(applicationContext, intent)
    }
}

class CategoryWidgetRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var counters: List<CounterItem> = emptyList()
    private val appWidgetId: Int = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    private val category: String? = intent.getStringExtra("category")

    data class CounterItem(
        val name: String,
        val count: Int,
        val goal: Int,
        val color: Int,
        val isStandard: Boolean
    )

    override fun onCreate() {
        Log.d(TAG, "onCreate for widget $appWidgetId, category $category")
    }

    override fun onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged for widget $appWidgetId")
        
        if (category == null) {
            counters = emptyList()
            return
        }

        val viewModel = (context.applicationContext as BetterApplication).viewModel
        val allCounters = viewModel.getCounterList()
        
        counters = allCounters
            .filter { name -> viewModel.getCounterCategory(name) == category }
            .mapNotNull { name ->
                try {
                    val summary = viewModel.getCounterSummary(name).value
                    if (summary != null) {
                        CounterItem(
                            name = summary.name,
                            count = summary.lastIntervalCount,
                            goal = summary.goal,
                            color = summary.color.colorInt,
                            isStandard = summary.type == org.kde.bettercounter.persistence.CounterType.STANDARD
                        )
                    } else null
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting counter summary for $name", e)
                    null
                }
            }
            .filter { counter -> counter.goal <= 0 || counter.count < counter.goal }
    }

    override fun onDestroy() {
        counters = emptyList()
    }

    override fun getCount(): Int = counters.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= counters.size) {
            return RemoteViews(context.packageName, R.layout.widget_category_item)
        }

        val counter = counters[position]
        val views = RemoteViews(context.packageName, R.layout.widget_category_item)

        views.setTextViewText(R.id.widgetCounterItemName, counter.name)
        
        val countText = if (counter.goal > 0) {
            "${counter.count}/${counter.goal}"
        } else {
            counter.count.toString()
        }
        views.setTextViewText(R.id.widgetCounterItemCount, countText)
        
        val bgColor = if (counter.isStandard) {
            adjustAlpha(counter.color, 0.3f)
        } else {
            adjustAlpha(0xFF808080.toInt(), 0.2f)
        }
        views.setInt(R.id.widgetCounterItemBackground, "setBackgroundColor", bgColor)

        if (counter.isStandard) {
            val fillInIntent = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra("counter_name", counter.name)
            }
            views.setOnClickFillInIntent(R.id.widgetCounterItemBackground, fillInIntent)
        }

        return views
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (255 * factor).toInt()
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long {
        return if (position < counters.size) {
            counters[position].name.hashCode().toLong()
        } else {
            position.toLong()
        }
    }

    override fun hasStableIds(): Boolean = true
}
