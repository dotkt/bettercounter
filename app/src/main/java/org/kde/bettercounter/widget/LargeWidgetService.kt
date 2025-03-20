package org.kde.bettercounter.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.R
import org.kde.bettercounter.ViewModel
import org.kde.bettercounter.persistence.CounterSummary
import org.kde.bettercounter.ui.MainActivity
import android.os.Bundle

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
        
        // 设置名称，限制只显示5个字符
        val displayName = if (summary.name.length > 5) {
            summary.name.substring(0, 5)
        } else {
            summary.name
        }
        rv.setTextViewText(R.id.widget_counter_name, displayName)
        
        // 使用实际计数值和目标值（如果有）
        val count = getCountFromSummary(summary)
        val goal = summary.goal ?: 0   // 假设CounterSummary有一个goal属性
        
        // 显示格式：如果达成目标则添加对号，然后是计数/目标
        val countText = if (goal > 0) {
            if (count >= goal) {
                "✓$count/$goal"
            } else {
                "$count/$goal"
            }
        } else {
            // 如果没有目标，不显示数字，只在计数为0时显示""
            if (count == 0) "" else count.toString()
        }
        rv.setTextViewText(R.id.widget_counter_count, countText)
        
        // 文本标签不填充Intent，只填充按钮
        
        // 增加按钮的填充意图 - 使用Bundle保证extras不丢失
        val incrementBundle = Bundle()
        incrementBundle.putString(EXTRA_COUNTER_NAME, summary.name)
        incrementBundle.putInt("COUNTER_POSITION", position)
        incrementBundle.putString("ACTION_TYPE", "INCREMENT")
        
        val incrementFillIntent = Intent()
        incrementFillIntent.putExtras(incrementBundle)
        // 设置数据URI以确保意图唯一
        incrementFillIntent.data = Uri.parse("counter://${summary.name}/increment/${System.currentTimeMillis()}")
        
        rv.setOnClickFillInIntent(R.id.widget_increase_button, incrementFillIntent)
        
        // 减少按钮设置
        val decrementBundle = Bundle()
        decrementBundle.putString(EXTRA_COUNTER_NAME, summary.name)
        decrementBundle.putInt("COUNTER_POSITION", position)
        decrementBundle.putString("ACTION_TYPE", "DECREMENT")
        
        val decrementFillIntent = Intent()
        decrementFillIntent.putExtras(decrementBundle)
        decrementFillIntent.data = Uri.parse("counter://${summary.name}/decrement/${System.currentTimeMillis()}")
        
        rv.setOnClickFillInIntent(R.id.widget_decrease_button, decrementFillIntent)
        
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

    private fun getCountFromSummary(summary: CounterSummary): Int {
        // 尝试不同的可能属性名
        return try {
            val field = CounterSummary::class.java.getDeclaredFields().find { 
                it.name in listOf("count", "total", "value", "number", "sum") 
            }
            
            if (field != null) {
                field.isAccessible = true
                field.get(summary) as? Int ?: 0
            } else {
                0 // 默认值
            }
        } catch (e: Exception) {
            android.util.Log.e("LargeWidgetFactory", "获取计数值失败: ${e.message}")
            0
        }
    }

    companion object {
        const val ACTION_INCREMENT = "org.kde.bettercounter.widget.ACTION_INCREMENT"
        const val ACTION_DECREMENT = "org.kde.bettercounter.widget.ACTION_DECREMENT"
    }
} 