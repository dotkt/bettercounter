package org.kde.bettercounter.ui


import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import io.github.douglasjunior.androidSimpleTooltip.SimpleTooltip.OnDismissListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kde.bettercounter.R
import org.kde.bettercounter.ViewModel
import org.kde.bettercounter.databinding.FragmentEntryBinding
import org.kde.bettercounter.persistence.CounterSummary
import org.kde.bettercounter.persistence.Tutorial
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class EntryViewHolder(
    private val activity: AppCompatActivity,
    val binding: FragmentEntryBinding,
    private var viewModel: ViewModel,
    private val touchHelper: ItemTouchHelper,
    private val onClickListener: (counter: CounterSummary) -> Unit?,
) : RecyclerView.ViewHolder(binding.root) {

    fun onBind(counter: CounterSummary, isMultiSelectMode: Boolean = false, isSelected: Boolean = false, onSelectionToggle: ((String) -> Unit)? = null) {
        binding.root.setBackgroundColor(counter.color.colorInt)
        
        // Handle visibility based on counter type FIRST
        when (counter.type) {
            org.kde.bettercounter.persistence.CounterType.STANDARD -> {
                binding.btnMinus1.visibility = android.view.View.VISIBLE
                binding.btnMinus5.visibility = android.view.View.VISIBLE
                binding.btnMinus10.visibility = android.view.View.VISIBLE
                binding.btnPlus1.visibility = android.view.View.VISIBLE
                binding.btnPlus5.visibility = android.view.View.VISIBLE
                binding.btnPlus10.visibility = android.view.View.VISIBLE
                binding.formulaIcon.visibility = android.view.View.GONE
            }
            org.kde.bettercounter.persistence.CounterType.DYNAMIC -> {
                binding.btnMinus1.visibility = android.view.View.GONE
                binding.btnMinus5.visibility = android.view.View.GONE
                binding.btnMinus10.visibility = android.view.View.GONE
                binding.btnPlus1.visibility = android.view.View.GONE
                binding.btnPlus5.visibility = android.view.View.GONE
                binding.btnPlus10.visibility = android.view.View.GONE
                binding.formulaIcon.visibility = android.view.View.VISIBLE
            }
        }

        // Multi-select mode overrides other UI states
        if (isMultiSelectMode) {
            binding.selectionCheckBox.visibility = android.view.View.VISIBLE
            binding.selectionCheckBox.isChecked = isSelected
            binding.selectionCheckBox.setOnClickListener {
                onSelectionToggle?.invoke(counter.name)
            }
            // 在多选模式下，点击名称也切换选择状态
            binding.nameText.setOnClickListener {
                onSelectionToggle?.invoke(counter.name)
            }
            binding.nameText.setOnLongClickListener(null)
            // 隐藏所有按钮和相对时间
            binding.buttonArea.visibility = android.view.View.GONE
            binding.btnStats.visibility = android.view.View.GONE
            binding.relativeTimeText.visibility = android.view.View.GONE
            binding.counterValueText.visibility = android.view.View.GONE
        } else {
            binding.selectionCheckBox.visibility = android.view.View.GONE
            binding.selectionCheckBox.isChecked = false
            // 显示所有按钮和内容
            binding.buttonArea.visibility = android.view.View.VISIBLE
            binding.btnStats.visibility = android.view.View.VISIBLE
        }
        
        // 使用一个变量来跟踪按钮点击时间，防止触发标题点击
        var lastButtonClickTime = 0L
        
        // 为TextView按钮设置圆角背景
        fun setButtonBackground(textView: android.widget.TextView, color: Int) {
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(color)
                cornerRadius = 8f
            }
            textView.background = drawable
        }
        
        setButtonBackground(binding.btnMinus10, android.graphics.Color.parseColor("#FF6B6B"))
        setButtonBackground(binding.btnMinus5, android.graphics.Color.parseColor("#FF6B6B"))
        setButtonBackground(binding.btnMinus1, android.graphics.Color.parseColor("#FF6B6B"))
        setButtonBackground(binding.btnPlus1, android.graphics.Color.parseColor("#90EE90"))
        setButtonBackground(binding.btnPlus5, android.graphics.Color.parseColor("#90EE90"))
        setButtonBackground(binding.btnPlus10, android.graphics.Color.parseColor("#90EE90"))
        setButtonBackground(binding.btnCustom, android.graphics.Color.parseColor("#A9A9A9"))
        
        // 红色减号按钮绑定
        binding.btnMinus10.setOnClickListener {
            val mostRecentDate = counter.mostRecent
            if (mostRecentDate != null && System.currentTimeMillis() - mostRecentDate.time <= 3600000L) {
                lastButtonClickTime = System.currentTimeMillis()
                viewModel.incrementCounterByValue(counter.name, -10)
            } else {
                android.widget.Toast.makeText(activity, "只能撤销一小时内的点击", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnMinus5.setOnClickListener {
            val mostRecentDate = counter.mostRecent
            if (mostRecentDate != null && System.currentTimeMillis() - mostRecentDate.time <= 3600000L) {
                lastButtonClickTime = System.currentTimeMillis()
                viewModel.incrementCounterByValue(counter.name, -5)
            } else {
                android.widget.Toast.makeText(activity, "只能撤销一小时内的点击", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnMinus1.setOnClickListener {
            val mostRecentDate = counter.mostRecent
            if (mostRecentDate != null && System.currentTimeMillis() - mostRecentDate.time <= 3600000L) {
                lastButtonClickTime = System.currentTimeMillis()
                viewModel.incrementCounterByValue(counter.name, -1)
            } else {
                android.widget.Toast.makeText(activity, "只能撤销一小时内的点击", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        // 绿色加号按钮绑定
        binding.btnPlus1.setOnClickListener { 
            lastButtonClickTime = System.currentTimeMillis()
            viewModel.incrementCounterByValue(counter.name, 1)
            if (!viewModel.isTutorialShown(Tutorial.PICK_DATE)) {
                viewModel.setTutorialShown(Tutorial.PICK_DATE)
                showPickDateTutorial()
            }
        }
        binding.btnPlus5.setOnClickListener { 
            lastButtonClickTime = System.currentTimeMillis()
            viewModel.incrementCounterByValue(counter.name, 5)
        }
        binding.btnPlus10.setOnClickListener { 
            lastButtonClickTime = System.currentTimeMillis()
            viewModel.incrementCounterByValue(counter.name, 10)
        }

        // 统计按钮
        binding.btnStats.setOnClickListener {
            lastButtonClickTime = System.currentTimeMillis()
            printStatistics(counter)
        }

        // 自定义按钮弹窗
        binding.btnCustom.setOnClickListener {
            lastButtonClickTime = System.currentTimeMillis()
            val editText = android.widget.EditText(activity)
            editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            editText.hint = "输入正负分数"
            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("自定义加减分数")
                .setView(editText)
                .setPositiveButton("确定") { _, _ ->
                    try {
                        val inputText = editText.text.toString().trim()
                        val normalizedText = inputText.replace('－', '-')
                        val value = normalizedText.toIntOrNull()
                        if (value != null && value != 0) {
                            if (value > 0) {
                                viewModel.incrementCounterByValue(counter.name, value)
                            } else {
                                viewModel.incrementCounterByValue(counter.name, value)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("EntryViewHolder", "处理自定义分数时出错: ${e.message}", e)
                        androidx.appcompat.app.AlertDialog.Builder(activity)
                            .setTitle("错误")
                            .setMessage("操作失败: ${e.message}")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        if (!isMultiSelectMode) {
            binding.nameText.setOnClickListener { 
                if (System.currentTimeMillis() - lastButtonClickTime > 300) {
                    onClickListener(counter)
                }
            }
            binding.nameText.setOnLongClickListener {
                touchHelper.startDrag(this@EntryViewHolder)
                @Suppress("DEPRECATION")
                binding.nameText.performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                true
            }
        }
        
        binding.nameText.text = counter.name

        val mostRecentDate = counter.mostRecent
        if (mostRecentDate != null && counter.type == org.kde.bettercounter.persistence.CounterType.STANDARD) {
            binding.relativeTimeText.referenceTime = mostRecentDate.time
            binding.relativeTimeText.visibility = android.view.View.VISIBLE
            updateRelativeTimeColor(counter, mostRecentDate)
        } else {
            binding.relativeTimeText.visibility = android.view.View.GONE
        }
        
        if (counter.goal > 0 && counter.type == org.kde.bettercounter.persistence.CounterType.STANDARD) {
            binding.counterValueText.text = "${counter.lastIntervalCount}/${counter.goal}"
        } else {
            binding.counterValueText.text = counter.lastIntervalCount.toString()
        }
        binding.counterValueText.visibility = android.view.View.VISIBLE
    }

    private fun updateGoal(counter: CounterSummary, newGoal: Int) {
        if (newGoal == counter.goal) return
        val category = viewModel.getCounterCategory(counter.name)
        val meta = org.kde.bettercounter.persistence.CounterMetadata(
            counter.name, counter.interval, newGoal, counter.color, category, counter.type, counter.formula
        )
        viewModel.editCounterSameName(meta)
    }
    
    /**
     * 更新相对时间的颜色，如果是"刚刚"状态则使用特殊颜色
     */
    private fun updateRelativeTimeColor(counter: CounterSummary, mostRecentDate: Date) {
        val diffInSeconds = (System.currentTimeMillis() - mostRecentDate.time) / 1000
        // 如果是"刚刚"（<60秒），使用鲜艳的红色字体
        if (diffInSeconds < 60) {
            // 检查背景色是否是红色系
            val backgroundColor = counter.color.colorInt
            val red = (backgroundColor shr 16) and 0xFF
            val green = (backgroundColor shr 8) and 0xFF
            val blue = backgroundColor and 0xFF
            // 如果红色分量明显大于绿色和蓝色，认为是红色背景
            val isRedBackground = red > green + 50 && red > blue + 50 && red > 150
            
            if (isRedBackground) {
                // 红色背景，显示白色
                binding.relativeTimeText.setTextColor(android.graphics.Color.WHITE)
            } else {
                // 非红色背景，显示鲜艳的红色
                binding.relativeTimeText.setTextColor(android.graphics.Color.parseColor("#FF0000"))
            }
            
            // 如果是"刚刚"状态，30秒后再次检查颜色
            binding.root.postDelayed({
                if (binding.relativeTimeText.visibility == android.view.View.VISIBLE) {
                    updateRelativeTimeColor(counter, mostRecentDate)
                }
            }, 30 * 1000L)
        } else {
            binding.relativeTimeText.setTextColor(activity.getColor(android.R.color.white))
        }
    }
    
    /**
     * 格式化相对时间显示
     * 小于60秒：显示"刚刚"
     * 大于等于60秒但小于1小时：显示几分钟前
     * 1天以内：显示几小时前
     * 超过1天但不超过30天：显示几天前
     * 超过30天但不超过12个月：显示几月前
     * 超过12个月：显示几年前
     */
    private fun formatRelativeTime(date: Date): String {
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

    fun showPickDateTutorial(onDismissListener: OnDismissListener? = null) {
        Tutorial.PICK_DATE.show(activity, binding.btnPlus1, onDismissListener)
    }

    private fun printStatistics(counter: CounterSummary) {
        val TAG = "CounterStatistics"
        activity.lifecycleScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) {
                    viewModel.getAllEntriesSortedByDate(counter.name)
                }
                
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                
                // 构建统计信息文本
                val statsText = StringBuilder()
                statsText.append("计数器: ${counter.name}\n\n")
                statsText.append("总计数: ${entries.size}\n")
                statsText.append("当前区间计数: ${counter.lastIntervalCount}\n")
                statsText.append("目标: ${counter.goal}\n")
                statsText.append("区间类型: ${counter.interval}\n\n")
                
                if (entries.isNotEmpty()) {
                    statsText.append("最早记录: ${sdf.format(entries.first().date)}\n")
                    statsText.append("最新记录: ${sdf.format(entries.last().date)}\n\n")
                    
                    // 计算平均间隔
                    if (entries.size > 1) {
                        val totalDays = (entries.last().date.time - entries.first().date.time) / (1000.0 * 60 * 60 * 24)
                        val avgDays = totalDays / (entries.size - 1)
                        val avgFrequency = if (totalDays > 0) (entries.size - 1) / totalDays else 0.0
                        statsText.append("总天数: ${String.format("%.2f", totalDays)} 天\n")
                        statsText.append("平均间隔: ${String.format("%.2f", avgDays)} 天\n")
                        statsText.append("平均频率: ${String.format("%.2f", avgFrequency)} 次/天\n\n")
                    }
                    
                    // 最近10条记录
                    statsText.append("--- 最近10条记录 ---\n")
                    entries.takeLast(10).forEachIndexed { index, entry ->
                        statsText.append("${index + 1}. ${sdf.format(entry.date)}\n")
                    }
                } else {
                    statsText.append("暂无记录")
                }
                
                // 同时输出到 Logcat
                Log.d(TAG, "========== 计数器统计: ${counter.name} ==========")
                Log.d(TAG, statsText.toString())
                Log.d(TAG, "==========================================")
                
                // 在手机上显示周统计对话框
                withContext(Dispatchers.Main) {
                    val dialogView = activity.layoutInflater.inflate(R.layout.fragment_week_statistics, null)
                    
                    // 直接创建并绑定周统计视图
                    val adapter = StatisticsDialogAdapter(entries, counter.name, null)
                    adapter.bindWeekStatisticsView(dialogView)
                    
                    val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                        .setTitle(counter.name)
                        .setView(dialogView)
                        .setPositiveButton("确定", null)
                        .create()
                    
                    // 设置对话框背景为白色
                    dialog.window?.setBackgroundDrawableResource(android.R.color.white)
                    
                    dialog.show()
                    
                    // 设置对话框全屏显示（需要在show()之后设置）
                    val window = dialog.window
                    window?.let {
                        val layoutParams = it.attributes
                        layoutParams.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        layoutParams.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        it.attributes = layoutParams
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取统计信息失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                        .setTitle("错误")
                        .setMessage("获取统计信息失败: ${e.message}")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }

}
