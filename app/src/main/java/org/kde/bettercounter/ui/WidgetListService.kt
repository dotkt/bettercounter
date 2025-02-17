package org.kde.bettercounter.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.BuildConfig
import org.kde.bettercounter.R
import org.kde.bettercounter.persistence.CounterSummary

private const val TAG = "WidgetListService"

class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetListViewsFactory(applicationContext)
    }
}

class WidgetListViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var counters: List<CounterSummary> = listOf()
    private val viewModel = (context.applicationContext as BetterApplication).viewModel

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        updateCounters()
    }

    override fun onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged")
        updateCounters()
    }

    private fun updateCounters() {
        // 使用 runBlocking 确保数据加载完成
        runBlocking {
            val counterList = viewModel.getCounterList()
            Log.d(TAG, "Found ${counterList.size} counters")
            
            counters = counterList.mapNotNull { name -> 
                viewModel.getCounterSummary(name).value?.also {
                    Log.d(TAG, "Counter: ${it.name} = ${it.getFormattedCount(true)}")
                }
            }
            Log.d(TAG, "Loaded ${counters.size} counter summaries")
        }
    }

    override fun onDestroy() {
        counters = listOf()
    }

    override fun getCount(): Int {
        Log.d(TAG, "getCount: ${counters.size}")
        return counters.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        Log.d(TAG, "getViewAt: $position")
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
            setOnClickFillInIntent(R.id.itemCount, fillInIntent)
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
} 