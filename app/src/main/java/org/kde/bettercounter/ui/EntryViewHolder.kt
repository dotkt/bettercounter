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

    fun onBind(counter: CounterSummary) {
        binding.root.setBackgroundColor(counter.color.colorInt)
        
        // 新加减按钮绑定
        binding.btnMinus10.setOnClickListener { viewModel.decrementCounterByValue(counter.name, 10) }
        binding.btnMinus5.setOnClickListener { viewModel.decrementCounterByValue(counter.name, 5) }
        binding.btnMinus1.setOnClickListener { viewModel.decrementCounterByValue(counter.name, 1) }
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

        binding.infoArea.setOnClickListener { onClickListener(counter) }
        binding.infoArea.setOnLongClickListener {
            touchHelper.startDrag(this@EntryViewHolder)
            @Suppress("DEPRECATION")
            binding.infoArea.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            true
        }
        binding.nameText.text = counter.name
        binding.statusText.text = if (counter.isGoalMet()) "(已完成)" else "(未完成)"
        binding.actualCountText.text = counter.lastIntervalCount.toString()
        binding.targetCountText.text = counter.goal.toString()
        binding.targetInput.setText("")

        // 目标+/-按钮
        binding.btnTargetMinus.setOnClickListener {
            val newGoal = (counter.goal - 1).coerceAtLeast(0)
            updateGoal(counter, newGoal)
        }
        binding.btnTargetPlus.setOnClickListener {
            val newGoal = counter.goal + 1
            updateGoal(counter, newGoal)
        }
        // 目标设定按钮
        binding.btnTargetSet.setOnClickListener {
            val input = binding.targetInput.text.toString().trim()
            val value = input.toIntOrNull()
            if (value != null && value >= 0) {
                updateGoal(counter, value)
            } else {
                binding.targetInput.error = "请输入有效数字"
            }
        }
        // 回车也可设定
        binding.targetInput.setOnEditorActionListener { v, actionId, event ->
            val input = binding.targetInput.text.toString().trim()
            val value = input.toIntOrNull()
            if (value != null && value >= 0) {
                updateGoal(counter, value)
                true
            } else {
                false
            }
        }

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
        val meta = org.kde.bettercounter.persistence.CounterMetadata(
            counter.name, counter.interval, newGoal, counter.color
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
                
                Log.d(TAG, "========== 计数器统计: ${counter.name} ==========")
                Log.d(TAG, "总计数: ${entries.size}")
                Log.d(TAG, "当前区间计数: ${counter.lastIntervalCount}")
                Log.d(TAG, "目标: ${counter.goal}")
                Log.d(TAG, "区间类型: ${counter.interval}")
                
                if (entries.isNotEmpty()) {
                    Log.d(TAG, "最早记录: ${sdf.format(entries.first().date)}")
                    Log.d(TAG, "最新记录: ${sdf.format(entries.last().date)}")
                    
                    // 计算平均间隔
                    if (entries.size > 1) {
                        val totalDays = (entries.last().date.time - entries.first().date.time) / (1000.0 * 60 * 60 * 24)
                        val avgDays = totalDays / (entries.size - 1)
                        Log.d(TAG, "总天数: ${String.format("%.2f", totalDays)}")
                        Log.d(TAG, "平均间隔: ${String.format("%.2f", avgDays)} 天")
                        Log.d(TAG, "平均频率: ${String.format("%.2f", (entries.size - 1) / totalDays)} 次/天")
                    }
                    
                    // 最近10条记录
                    Log.d(TAG, "--- 最近10条记录 ---")
                    entries.takeLast(10).forEachIndexed { index, entry ->
                        Log.d(TAG, "${index + 1}. ${sdf.format(entry.date)}")
                    }
                } else {
                    Log.d(TAG, "暂无记录")
                }
                
                Log.d(TAG, "==========================================")
            } catch (e: Exception) {
                Log.e(TAG, "获取统计信息失败: ${e.message}", e)
            }
        }
    }

}
