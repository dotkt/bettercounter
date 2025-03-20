package org.kde.bettercounter.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.R
import org.kde.bettercounter.ViewModel
import org.kde.bettercounter.persistence.CounterSummary
import org.kde.bettercounter.ui.MainActivity

// 定义常量，以防MainActivity中没有定义
private const val EXTRA_COUNTER_NAME = "EXTRA_COUNTER_NAME"

/**
 * 为大型Widget提供数据的服务
 */
class LargeWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return LargeWidgetFactory(applicationContext, intent)
    }
}

/**
 * 为Widget列表提供数据的工厂类
 */
class LargeWidgetFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {
    
    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID, 
        AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private val groupId = intent.getStringExtra("GROUP_ID") ?: "default"
    private var counters: List<String> = emptyList()
    private var counterSummaries: List<CounterSummary> = emptyList()
    private lateinit var viewModel: ViewModel
    
    override fun onCreate() {
        viewModel = (context.applicationContext as BetterApplication).viewModel
        updateCounters()
    }
    
    override fun onDataSetChanged() {
        android.util.Log.d("LargeWidgetFactory", "刷新数据")
        updateCounters()
    }
    
    override fun onDestroy() {
        // 清理资源
        counters = emptyList()
        counterSummaries = emptyList()
    }
    
    override fun getCount(): Int = counters.size
    
    override fun getViewAt(position: Int): RemoteViews {
        if (position >= counters.size) {
            return RemoteViews(context.packageName, R.layout.counter_widget_item)
        }
        
        val counterName = counters[position]
        val summary = viewModel.getCounterSummary(counterName).value ?: return RemoteViews(context.packageName, R.layout.counter_widget_item)
        
        // 创建列表项视图
        val rv = RemoteViews(context.packageName, R.layout.counter_widget_item)
        rv.setTextViewText(R.id.widget_counter_name, summary.name)
        
        // 暂时使用固定值
        rv.setTextViewText(R.id.widget_counter_count, "0")
        
        // 设置点击事件填充项
        val fillInIntent = Intent().apply {
            putExtra(EXTRA_COUNTER_NAME, summary.name)
        }
        rv.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)
        
        return rv
    }
    
    override fun getLoadingView(): RemoteViews? = null
    
    override fun getViewTypeCount(): Int = 1
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    override fun hasStableIds(): Boolean = true
    
    private fun updateCounters() {
        // 获取所有计数器，并根据分组ID过滤
        val allCounters = viewModel.getCounterList()
        android.util.Log.d("LargeWidgetFactory", "所有计数器数量: ${allCounters.size}")
        
        counters = allCounters.filter { viewModel.getCounterGroup(it) == groupId }
        android.util.Log.d("LargeWidgetFactory", "分组 $groupId 的计数器数量: ${counters.size}")
        
        // 获取计数器摘要
        counterSummaries = counters.mapNotNull { 
            viewModel.getCounterSummary(it).value 
        }
    }
} 