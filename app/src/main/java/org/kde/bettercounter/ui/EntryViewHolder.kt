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
            editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            editText.hint = "输入正负分数"
            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("自定义加减分数")
                .setView(editText)
                .setPositiveButton("确定") { _, _ ->
                    val value = editText.text.toString().toIntOrNull()
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
        binding.countText.text = counter.getFormattedCount()

        val checkDrawable = if (counter.isGoalMet()) R.drawable.ic_check else 0
        binding.countText.setCompoundDrawablesRelativeWithIntrinsicBounds(checkDrawable, 0, 0, 0)

        val mostRecentDate = counter.mostRecent
        if (mostRecentDate != null) {
            binding.timestampText.referenceTime = mostRecentDate.time
        } else {
            binding.timestampText.referenceTime = -1L
        }
    }

    fun showPickDateTutorial(onDismissListener: OnDismissListener? = null) {
        Tutorial.PICK_DATE.show(activity, binding.btnPlus1, onDismissListener)
    }

}
