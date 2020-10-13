package org.kde.bettercounter

import android.text.format.DateUtils
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kde.bettercounter.persistence.Counter


class EntryViewHolder(var view: View, var viewModel: ViewModel) : RecyclerView.ViewHolder(view) {
    private val countText: TextView = view.findViewById(R.id.count)
    private val nameText: TextView = view.findViewById(R.id.name)
    private val lastEditText : TextView = view.findViewById(R.id.last_edit)
    private val increaseButton: ImageButton = view.findViewById(R.id.btn_increase)
    private val undoButton: ImageButton = view.findViewById(R.id.btn_undo)

    fun onBind(counter: Counter) {
        increaseButton.setOnClickListener { viewModel.incrementCounter(counter.name) }
        undoButton.setOnClickListener { viewModel.decrementCounter(counter.name) }
        nameText.text = counter.name
        countText.text = counter.count.toString()
        val lastEditDate = counter.lastEdit
        if (lastEditDate != null) {
            lastEditText.text = DateUtils.getRelativeTimeSpanString(lastEditDate.time)
            undoButton.isEnabled = true
        } else {
            lastEditText.setText(R.string.never)
            undoButton.isEnabled = false
        }
    }
}