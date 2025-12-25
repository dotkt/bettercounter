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
        
        // 多选模式处理
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
        } else {
            binding.selectionCheckBox.visibility = android.view.View.GONE
            binding.selectionCheckBox.isChecked = false
        }
        
        // 绿色加号按钮绑定
        binding.btnPlus1.setOnClickListener {
            viewModel.incrementCounterByValue(counter.name, 1)
            if (!viewModel.isTutorialShown(Tutorial.PICK_DATE)) {
                viewModel.setTutorialShown(Tutorial.PICK_DATE)
                showPickDateTutorial()
            }
        }
        binding.btnPlus5.setOnClickListener { viewModel.incrementCounterByValue(counter.name, 5) }
        binding.btnPlus10.setOnClickListener { viewModel.incrementCounterByValue(counter.name, 10) }

        // 统计按钮
        binding.btnStats.setOnClickListener {
            printStatistics(counter)
        }

        // 自定义按钮弹窗
        binding.btnCustom.setOnClickListener {
            val editText = android.widget.EditText(activity)
            editText.inputType = android.text.InputType.TYPE_CLASS_TEXT
            editText.hint = "输入正负分数"
            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("自定义加减分数")
                .setView(editText)
                .setPositiveButton("确定") { _, _ ->
                    val inputText = editText.text.toString().trim()
                    // 将中文减号替换为英文减号
                    val normalizedText = inputText.replace('－', '-')
                    val value = normalizedText.toIntOrNull()
                    if (value != null && value != 0) {
                        if (value > 0) viewModel.incrementCounterByValue(counter.name, value)
                        else viewModel.decrementCounterByValue(counter.name, -value)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 设置点击和长按事件（仅非多选模式）
        if (!isMultiSelectMode) {
            binding.nameText.setOnClickListener { onClickListener(counter) }
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
        if (mostRecentDate != null) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            binding.timestampText.text = "上次时间: ${sdf.format(mostRecentDate)}"
        } else {
            binding.timestampText.text = "上次时间: -"
        }
    }

    private fun updateGoal(counter: CounterSummary, newGoal: Int) {
        if (newGoal == counter.goal) return
        val category = viewModel.getCounterCategory(counter.name)
        val meta = org.kde.bettercounter.persistence.CounterMetadata(
            counter.name, counter.interval, newGoal, counter.color, category
        )
        viewModel.editCounterSameName(meta)
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
