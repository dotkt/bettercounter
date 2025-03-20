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
import org.kde.bettercounter.ui.WidgetProvider

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
    
    // 添加一个计数器值的Map
    private val counterValues = mutableMapOf<String, Int>()
    
    override fun onCreate() {
        viewModel = (context.applicationContext as BetterApplication).viewModel
        updateCounters()
    }
    
    override fun onDataSetChanged() {
        android.util.Log.d("LargeWidgetFactory", "刷新数据")
        updateCounters()
        
        // 直接使用ViewModel的getCounterValue方法
        counters.forEach { counterName ->
            try {
                val count = viewModel.getCounterValue(counterName)
                counterValues[counterName] = count
                android.util.Log.d("LargeWidgetFactory", "获取计数值: $counterName = $count")
            } catch (e: Exception) {
                android.util.Log.e("LargeWidgetFactory", "获取计数失败: ${e.message}")
                counterValues[counterName] = 0
            }
        }
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
        val summary = viewModel.getCounterSummary(counterName).value ?: 
            return RemoteViews(context.packageName, R.layout.counter_widget_item)
        
        // 创建列表项视图
        val rv = RemoteViews(context.packageName, R.layout.counter_widget_item)
        
        // 设置名称，限制只显示5个字符
        val displayName = if (summary.name.length > 5) {
            summary.name.substring(0, 5)
        } else {
            summary.name
        }
        rv.setTextViewText(R.id.widget_counter_name, displayName)
        
        // 直接从ViewModel获取正确的计数值
        val count = counterValues[counterName] ?: 0
        val goal = summary.goal ?: 0
        
        android.util.Log.d("WidgetAction", "计数器 ${summary.name} 当前计数: $count, 目标: $goal")
        
        // 设置目标达成状态 - 只在真正达到目标时显示
        if (goal > 0 && count >= goal) {
            android.util.Log.d("WidgetAction", "计数器 ${summary.name} 已达成目标")
            rv.setTextViewText(R.id.widget_goal_status, "👍")
        } else {
            rv.setTextViewText(R.id.widget_goal_status, "")
        }
        
        // 设置计数文本 - 特别注意：如果count=0就不显示数字
        val countText = if (goal > 0) {
            if (count == 0) {
                "0/$goal"  // 对于有目标的计数器，即使是0也要显示
            } else {
                "$count/$goal"
            }
        } else {
            if (count == 0) {
                ""  // 如果计数为0且没有目标，就不显示任何内容
            } else {
                count.toString()
            }
        }
        rv.setTextViewText(R.id.widget_counter_count, countText)
        
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
        
        // 简化：不在这里尝试获取计数值
    }

    private fun getCountFromSummary(summary: CounterSummary): Int {
        try {
            // 记录整个对象的内容
            android.util.Log.d("WidgetAction", "CounterSummary对象: $summary")
            
            // 直接查找所有字段并打印值
            val fields = summary.javaClass.declaredFields
            android.util.Log.d("WidgetAction", "发现 ${fields.size} 个字段")
            
            for (field in fields) {
                field.isAccessible = true
                try {
                    val value = field.get(summary)
                    android.util.Log.d("WidgetAction", "===> 字段 ${field.name} = $value (${value?.javaClass?.simpleName})")
                    
                    // 对于"total"字段特别处理（可能是计数值）
                    if (field.name == "total" && value is Int) {
                        return value
                    }
                } catch (e: Exception) {
                    // 忽略访问错误
                }
            }
            
            // 找不到合适的字段，尝试从toString中提取数字
            val text = summary.toString()
            android.util.Log.d("WidgetAction", "CounterSummary.toString(): $text")
            
            // 查找第一个数字序列
            val match = Regex("""(\d+)""").find(text)
            if (match != null) {
                val value = match.groupValues[1].toIntOrNull() ?: 0
                android.util.Log.d("WidgetAction", "从toString()中提取到的数字: $value")
                return value
            }
            
            return 0
        } catch (e: Exception) {
            android.util.Log.e("WidgetAction", "获取计数值失败: ${e.message}")
            e.printStackTrace()
            return 0
        }
    }

    companion object {
        const val ACTION_INCREMENT = "org.kde.bettercounter.widget.ACTION_INCREMENT"
        const val ACTION_DECREMENT = "org.kde.bettercounter.widget.ACTION_DECREMENT"
    }
} 