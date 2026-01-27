package org.kde.bettercounter.ui

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager.LayoutParams
import android.widget.AdapterView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import org.kde.bettercounter.ColorAdapter
import org.kde.bettercounter.IntervalAdapter
import org.kde.bettercounter.R
import org.kde.bettercounter.ViewModel
import org.kde.bettercounter.databinding.CounterSettingsBinding
import org.kde.bettercounter.persistence.CounterColor
import org.kde.bettercounter.persistence.CounterMetadata
import org.kde.bettercounter.persistence.CounterSummary
import org.kde.bettercounter.persistence.CounterType
import org.kde.bettercounter.persistence.Interval

class CounterSettingsDialogBuilder(private val context: Context, private val viewModel: ViewModel) {

    private val builder = MaterialAlertDialogBuilder(context)
    private val binding: CounterSettingsBinding = CounterSettingsBinding.inflate(LayoutInflater.from(context))
    private val intervalAdapter = IntervalAdapter(context)
    private val colorAdapter = ColorAdapter(context)
    private var onSaveListener: (counterMetadata: CounterMetadata) -> Unit = { _ -> }
    private var previousName: String? = null
    private var goal = 0
    private var step = 1
    private var currentType = CounterType.STANDARD

    init {
        builder.setView(binding.root)

        binding.spinnerInterval.adapter = intervalAdapter

        binding.fakeSpinnerIntervalBox.setOnClickListener {
            binding.spinnerInterval.performClick()
        }
        binding.fakeSpinnerInterval.setOnClickListener {
            binding.spinnerInterval.performClick()
        }
        binding.fakeSpinnerIntervalBox.endIconMode = TextInputLayout.END_ICON_CUSTOM
        binding.fakeSpinnerIntervalBox.endIconDrawable = AppCompatResources.getDrawable(context, com.google.android.material.R.drawable.mtrl_dropdown_arrow)
        binding.fakeSpinnerInterval.isLongClickable = false
        binding.spinnerInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long
            ) {
                binding.fakeSpinnerInterval.setText(intervalAdapter.getItem(position))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }

        binding.colorpicker.adapter = colorAdapter
        binding.colorpicker.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        binding.goalInputBox.setStartIconOnClickListener {
            if (goal > 0) {
                goal -= 1
                updateGoalText()
            }
        }
        binding.goalInputBox.setEndIconOnClickListener {
            goal += 1
            updateGoalText()
        }

        binding.goalInput.addTextChangedListener {
            goal = it.toString().toIntOrNull() ?: 0
            if (goal == 0) {
                it?.clear()
            }
            binding.goalInput.isCursorVisible = binding.goalInput.hasFocus() && (goal != 0)
        }

        binding.goalInput.setOnFocusChangeListener { _, hasFocus ->
            binding.goalInput.isCursorVisible = hasFocus && (goal != 0)
        }

        binding.stepInputBox.setStartIconOnClickListener {
            if (step > 1) {
                step -= 1
                updateStepText()
            }
        }
        binding.stepInputBox.setEndIconOnClickListener {
            step += 1
            updateStepText()
        }

        binding.stepInput.addTextChangedListener {
            step = it.toString().toIntOrNull() ?: 1
            if (step == 1) {
                it?.clear()
            }
            binding.stepInput.isCursorVisible = binding.stepInput.hasFocus() && (step != 1)
        }

        binding.stepInput.setOnFocusChangeListener { _, hasFocus ->
            binding.stepInput.isCursorVisible = hasFocus && (step != 1)
        }

        binding.counterTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioStandard -> {
                    currentType = CounterType.STANDARD
                    binding.standardCounterSettings.visibility = View.VISIBLE
                    binding.formulaInputBox.visibility = View.GONE
                }
                R.id.radioDynamic -> {
                    currentType = CounterType.DYNAMIC
                    binding.standardCounterSettings.visibility = View.GONE
                    binding.formulaInputBox.visibility = View.VISIBLE
                }
            }
        }

        builder.setPositiveButton(R.string.save, null)
        builder.setNegativeButton(R.string.cancel, null)
    }

    private fun updateGoalText() {
        if (goal > 0) {
            binding.goalInput.setText(goal.toString())
        } else {
            binding.goalInput.text?.clear() // will show the hint, which is "Ø"
        }
    }

    private fun updateStepText() {
        if (step > 1) {
            binding.stepInput.setText(step.toString())
        } else {
            binding.stepInput.text?.clear()
        }
    }

    fun forNewCounter(): CounterSettingsDialogBuilder {
        builder.setTitle(R.string.add_counter)
        binding.fakeSpinnerInterval.setText(Interval.DEFAULT.toHumanReadableResourceId())
        binding.spinnerInterval.setSelection(intervalAdapter.positionOf(Interval.DEFAULT))
        binding.categoryEdit.setText("默认")
        updateGoalText()
        updateStepText()
        return this
    }

    fun forExistingCounter(counter: CounterSummary): CounterSettingsDialogBuilder {
        builder.setTitle(R.string.edit_counter)
        previousName = counter.name
        binding.nameEditBox.isHintAnimationEnabled = false
        binding.nameEdit.setText(counter.name)
        binding.nameEditBox.isHintAnimationEnabled = true

        colorAdapter.selectedColor = counter.color.colorInt
        
        val category = viewModel.getCounterCategory(counter.name)
        binding.categoryEdit.setText(category)
        
        // Set type and show/hide fields accordingly
        currentType = counter.type
        if (currentType == CounterType.DYNAMIC) {
            binding.radioDynamic.isChecked = true
            binding.formulaEdit.setText(counter.formula)
            binding.standardCounterSettings.visibility = View.GONE
            binding.formulaInputBox.visibility = View.VISIBLE
        } else {
            binding.radioStandard.isChecked = true
            binding.fakeSpinnerInterval.setText(counter.interval.toHumanReadableResourceId())
            binding.spinnerInterval.setSelection(intervalAdapter.positionOf(counter.interval))
            goal = counter.goal
            step = counter.step
            updateGoalText()
            updateStepText()
            binding.standardCounterSettings.visibility = View.VISIBLE
            binding.formulaInputBox.visibility = View.GONE
        }
        
        return this
    }

    fun setOnSaveListener(onSave: (counterMetadata: CounterMetadata) -> Unit): CounterSettingsDialogBuilder {
        onSaveListener = onSave
        return this
    }

    fun setOnDeleteListener(onClickListener: DialogInterface.OnClickListener): CounterSettingsDialogBuilder {
        builder.setNeutralButton(R.string.delete_or_reset, onClickListener)
        return this
    }

    fun setOnDismissListener(onClickListener: DialogInterface.OnDismissListener): CounterSettingsDialogBuilder {
        builder.setOnDismissListener(onClickListener)
        return this
    }

    fun show(): AlertDialog {
        val dialog = builder.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = binding.nameEdit.text.toString().trim()
            var isValid = true

            if (name.isBlank()) {
                binding.nameEdit.error = context.getString(R.string.name_cant_be_blank)
                isValid = false
            } else if (name != previousName && viewModel.counterExists(name)) {
                binding.nameEdit.error = context.getString(R.string.already_exists)
                isValid = false
            } else {
                binding.nameEdit.error = null
            }

            val formula = binding.formulaEdit.text.toString().trim()
            if (currentType == CounterType.DYNAMIC) {
                val validationResult = viewModel.validateFormula(formula, name)
                if (validationResult is ViewModel.FormulaValidationResult.Invalid) {
                    binding.formulaInputBox.error = validationResult.error
                    isValid = false
                } else {
                    binding.formulaInputBox.error = null
                }
            }
            
            if (isValid) {
                val category = binding.categoryEdit.text.toString().trim().takeIf { it.isNotBlank() } ?: "默认"
                val metadata = if (currentType == CounterType.STANDARD) {
                    CounterMetadata(
                        name,
                        intervalAdapter.itemAt(binding.spinnerInterval.selectedItemPosition),
                        goal,
                        CounterColor(colorAdapter.selectedColor),
                        category,
                        CounterType.STANDARD,
                        null,
                        step
                    )
                } else { // DYNAMIC
                    CounterMetadata(
                        name,
                        Interval.DEFAULT, // Interval/goal not applicable for dynamic
                        0,
                        CounterColor(colorAdapter.selectedColor),
                        category,
                        CounterType.DYNAMIC,
                        formula,
                        1
                    )
                }
                onSaveListener(metadata)
                dialog.dismiss()
            }
        }
        if (binding.nameEdit.text.isNullOrEmpty()) {
            binding.nameEdit.requestFocus()
            dialog.window?.setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        return dialog
    }
}
