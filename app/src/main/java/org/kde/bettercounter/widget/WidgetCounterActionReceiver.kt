package org.kde.bettercounter.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.R
import org.kde.bettercounter.ui.MainActivity
import org.kde.bettercounter.persistence.CounterSummary
import kotlin.reflect.KClass

/**
 * 接收Widget上的计数操作(+/-)的广播接收器
 */
class WidgetCounterActionReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        android.util.Log.d("WidgetAction", "接收到广播: $action")
        
        // 调试所有Intent参数
        intent.extras?.keySet()?.forEach { key ->
            val value = intent.extras?.get(key)
            android.util.Log.d("WidgetAction", "参数 $key = $value")
        }
        
        when (action) {
            "org.kde.bettercounter.widget.HANDLE_CLICK" -> {
                // 处理列表项点击事件
                val actionType = intent.getStringExtra("ACTION_TYPE")
                val counterName = intent.getStringExtra(EXTRA_COUNTER_NAME)
                
                android.util.Log.d("WidgetAction", "处理点击事件: actionType=$actionType, 计数器=$counterName")
                
                if (actionType == null || counterName == null) {
                    android.util.Log.e("WidgetAction", "缺少必要参数: actionType=$actionType, counterName=$counterName")
                    return
                }
                
                val viewModel = (context.applicationContext as BetterApplication).viewModel
                android.util.Log.d("WidgetAction", "获取到ViewModel: $viewModel")
                
                when (actionType) {
                    "INCREMENT" -> {
                        android.util.Log.d("WidgetAction", "尝试直接增加计数")
                        try {
                            viewModel.incrementCounter(counterName)
                            android.util.Log.d("WidgetAction", "直接增加计数成功")
                            updateWidgets(context)
                        } catch (e: Exception) {
                            android.util.Log.e("WidgetAction", "直接增加计数失败: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                    "DECREMENT" -> {
                        android.util.Log.d("WidgetAction", "尝试直接减少计数")
                        try {
                            viewModel.decrementCounter(counterName)
                            android.util.Log.d("WidgetAction", "直接减少计数成功")
                            updateWidgets(context)
                        } catch (e: Exception) {
                            android.util.Log.e("WidgetAction", "直接减少计数失败: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                    "VIEW" -> {
                        // 启动主活动查看计数器
                        val viewIntent = Intent(context, MainActivity::class.java).apply {
                            putExtra(EXTRA_COUNTER_NAME, counterName)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(viewIntent)
                    }
                }
            }
            
            ACTION_INCREMENT -> {
                val counterName = intent.getStringExtra(EXTRA_COUNTER_NAME) ?: return
                android.util.Log.d("WidgetAction", "尝试增加计数: $counterName")
                try {
                    handleIncrement(context, counterName)
                } catch (e: Exception) {
                    android.util.Log.e("WidgetAction", "增加计数失败: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            ACTION_DECREMENT -> {
                val counterName = intent.getStringExtra(EXTRA_COUNTER_NAME) ?: return
                android.util.Log.d("WidgetAction", "尝试减少计数: $counterName")
                try {
                    handleDecrement(context, counterName)
                } catch (e: Exception) {
                    android.util.Log.e("WidgetAction", "减少计数失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
    
    private fun handleIncrement(context: Context, counterName: String) {
        val viewModel = (context.applicationContext as BetterApplication).viewModel
        
        // 先获取当前计数
        val summary = viewModel.getCounterSummary(counterName).value
        val currentCount = getCountFromSummary(summary)
        android.util.Log.d("WidgetAction", "当前计数: $currentCount")
        
        // 增加计数
        try {
            viewModel.incrementCounter(counterName)
            
            // 验证计数是否真的增加了
            val newSummary = viewModel.getCounterSummary(counterName).value
            val newCount = getCountFromSummary(newSummary)
            android.util.Log.d("WidgetAction", "新计数: $newCount, 增加了: ${newCount - currentCount}")
            
            updateWidgets(context)
        } catch (e: Exception) {
            android.util.Log.e("WidgetAction", "增加计数失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun handleDecrement(context: Context, counterName: String) {
        val viewModel = (context.applicationContext as BetterApplication).viewModel
        
        android.util.Log.d("WidgetAction", "减少计数: $counterName")
        try {
            viewModel.decrementCounter(counterName)
            updateWidgets(context)
            android.util.Log.d("WidgetAction", "减少计数成功")
        } catch (e: Exception) {
            android.util.Log.e("WidgetAction", "减少计数失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun updateWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, LargeCounterWidget::class.java)
        )
        
        android.util.Log.d("WidgetAction", "更新Widget IDs: ${appWidgetIds.joinToString()}")
        
        // 通知数据变化
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
    }
    
    private fun getCountFromSummary(summary: CounterSummary?): Int {
        if (summary == null) return 0
        
        // 只使用反射获取计数
        return try {
            // 尝试所有可能的字段名
            val possibleFields = listOf("count", "total", "value", "number", "currentCount", "sum", "counts")
            
            for (fieldName in possibleFields) {
                try {
                    val field = CounterSummary::class.java.getDeclaredField(fieldName)
                    field.isAccessible = true
                    val value = field.get(summary)
                    if (value is Int) {
                        android.util.Log.d("WidgetAction", "找到计数字段: $fieldName = $value")
                        return value
                    }
                } catch (e: Exception) {
                    // 继续尝试下一个字段
                    continue
                }
            }
            
            // 尝试getDeclaredFields()找所有字段
            android.util.Log.d("WidgetAction", "尝试检查所有字段")
            val fields = CounterSummary::class.java.declaredFields
            for (field in fields) {
                field.isAccessible = true
                try {
                    val value = field.get(summary)
                    android.util.Log.d("WidgetAction", "字段 ${field.name} = $value (${value?.javaClass?.simpleName})")
                    if (value is Int && field.name != "hashCode") {
                        return value
                    }
                } catch (e: Exception) {
                    // 忽略访问错误
                }
            }
            
            0 // 如果所有尝试都失败
        } catch (e: Exception) {
            android.util.Log.e("WidgetAction", "获取计数值失败: ${e.message}")
            e.printStackTrace()
            0
        }
    }
    
    companion object {
        private const val EXTRA_COUNTER_NAME = "EXTRA_COUNTER_NAME"
        const val ACTION_INCREMENT = "org.kde.bettercounter.widget.ACTION_INCREMENT"
        const val ACTION_DECREMENT = "org.kde.bettercounter.widget.ACTION_DECREMENT"
    }
} 