package org.kde.bettercounter.ui

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.BuildConfig
import org.kde.bettercounter.R
import org.kde.bettercounter.persistence.CounterSummary

class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetListViewsFactory(applicationContext)
    }
}

class WidgetListViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var counters: List<CounterSummary> = listOf()
    private val viewModel = (context.applicationContext as BetterApplication).viewModel

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val counterList = viewModel.getCounterList()
        counters = counterList.mapNotNull { name -> 
            viewModel.getCounterSummary(name).value
        }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = counters.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= counters.size) {
            return RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_list_item).apply {
                setTextViewText(R.id.itemName, "")
                setTextViewText(R.id.itemCount, "")
            }
        }

        val counter = counters[position]
        return RemoteViews(BuildConfig.APPLICATION_ID, R.layout.widget_list_item).apply {
            setTextViewText(R.id.itemName, counter.name)
            setTextViewText(R.id.itemCount, counter.getFormattedCount(forWidget = true))
            
            // 设置点击事件的 fillInIntent
            val fillInIntent = Intent().apply {
                putExtra(MainActivity.EXTRA_COUNTER_NAME, counter.name)
            }
            setOnClickFillInIntent(R.id.itemName, fillInIntent)
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
} 