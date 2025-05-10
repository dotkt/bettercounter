package org.kde.bettercounter.ui


import android.view.HapticFeedbackConstants
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import io.github.douglasjunior.androidSimpleTooltip.SimpleTooltip.OnDismissListener
import org.kde.bettercounter.R
import org.kde.bettercounter.ViewModel
import org.kde.bettercounter.databinding.FragmentEntryBinding
import org.kde.bettercounter.persistence.CounterSummary
import org.kde.bettercounter.persistence.Tutorial
import java.util.Calendar

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
            val now = System.currentTimeMillis()
            val diff = now - mostRecentDate.time
            val minute = 60 * 1000
            val hour = 60 * minute
            val day = 24 * hour
            val text = when {
                diff < minute -> "上次时间: 刚刚"
                diff < hour -> "上次时间: ${diff / minute} 分钟前"
                diff < day -> "上次时间: ${diff / hour} 小时前"
                else -> {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    "上次时间: ${sdf.format(mostRecentDate)}"
                }
            }
            binding.timestampText.text = text
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

}
