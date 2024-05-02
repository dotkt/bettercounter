package org.kde.bettercounter.ui


import android.view.Gravity
import android.view.HapticFeedbackConstants
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import io.github.douglasjunior.androidSimpleTooltip.SimpleTooltip
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

    fun showPickDateTutorial(onDismissListener: OnDismissListener? = null) {
        SimpleTooltip.Builder(activity)
            .anchorView(binding.increaseButton)
            .text(R.string.tutorial_pickdate)
            .gravity(Gravity.BOTTOM)
            .animated(true)
            .focusable(true) // modal requires focusable
            .modal(true)
            .onDismissListener(onDismissListener)
            .build()
            .show()
    }

    fun onBind(counter: CounterSummary) {
        binding.root.setBackgroundColor(counter.color.colorInt)
        binding.increaseButton.setOnClickListener {
            viewModel.incrementCounter(counter.name)
            if (!viewModel.isTutorialShown(Tutorial.PICK_DATE)) {
                viewModel.setTutorialShown(Tutorial.PICK_DATE)
                showPickDateTutorial()
            }
        }
        binding.increaseButton.setOnLongClickListener {
            showDateTimePicker(activity, Calendar.getInstance()) { pickedDateTime ->
                viewModel.incrementCounter(counter.name, pickedDateTime.time)
            }
            true
        }
        binding.decreaseButton.setOnClickListener { viewModel.decrementCounter(counter.name) }
        binding.draggableArea.setOnClickListener { onClickListener(counter) }
        binding.draggableArea.setOnLongClickListener {
            touchHelper.startDrag(this@EntryViewHolder)
            @Suppress("DEPRECATION")
            binding.draggableArea.performHapticFeedback(
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
            binding.decreaseButton.isEnabled = true
        } else {
            binding.timestampText.referenceTime = -1L
            binding.decreaseButton.isEnabled = false
        }
    }
}
