package org.kde.bettercounter

import android.annotation.SuppressLint
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import io.github.douglasjunior.androidSimpleTooltip.SimpleTooltip
import io.github.douglasjunior.androidSimpleTooltip.SimpleTooltip.OnDismissListener
import org.kde.bettercounter.boilerplate.DragAndSwipeTouchHelper
import org.kde.bettercounter.databinding.FragmentEntryBinding
import org.kde.bettercounter.persistence.CounterSummary
import org.kde.bettercounter.persistence.Tutorial
import org.kde.bettercounter.ui.EntryViewHolder
import java.util.Collections
import java.util.Date

private const val TAG = "EntryListAdapter"

class EntryListViewAdapter(
    private var activity: AppCompatActivity,
    private var viewModel: ViewModel,
    private var listObserver: EntryListObserver,
    private var groupId: String = "default"
) : RecyclerView.Adapter<EntryViewHolder>(), DragAndSwipeTouchHelper.ListGesturesCallback {
    interface EntryListObserver {
        fun onItemSelected(position: Int, counter: CounterSummary)
        fun onSelectedItemUpdated(position: Int, counter: CounterSummary)
        fun onItemAdded(position: Int)
    }

    var currentSelectedCounterName: String? = null
    fun clearItemSelected() { currentSelectedCounterName = null }

    private val inflater: LayoutInflater = LayoutInflater.from(activity)
    private var counters: MutableList<String> = mutableListOf()

    override fun getItemCount(): Int = counters.size

    private val touchHelper = ItemTouchHelper(DragAndSwipeTouchHelper(this))

    private var recyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(view: RecyclerView) {
        recyclerView = view
        touchHelper.attachToRecyclerView(view)
        super.onAttachedToRecyclerView(view)
    }

    private fun observeNewCounter(counterName: String) {
        viewModel.getCounterSummary(counterName).removeObservers(activity)
        
        viewModel.getCounterSummary(counterName).observe(activity) {
            val position = counters.indexOf(it.name)
            if (position >= 0) {
                notifyItemChanged(position, Unit)
                if (currentSelectedCounterName == it.name) {
                    listObserver.onSelectedItemUpdated(position, it)
                }
            }
        }
    }

    init {
        viewModel.observeCounterChange(object : ViewModel.CounterObserver {
            @SuppressLint("NotifyDataSetChanged")
            override fun onInitialCountersLoaded() {
                activity.runOnUiThread {
                    refreshCounters()
                }
            }

            override fun onCounterAdded(counterName: String) {
                val group = viewModel.getCounterGroup(counterName)
                if (group == groupId) {
                    activity.runOnUiThread {
                        if (!counters.contains(counterName)) {
                            counters.add(counterName)
                            notifyItemInserted(counters.size - 1)
                            observeNewCounter(counterName)
                        }
                    }
                }
            }

            override fun onCounterRemoved(counterName: String) {
                val position = counters.indexOf(counterName)
                counters.removeAt(position)
                if (currentSelectedCounterName == counterName) {
                    currentSelectedCounterName = null
                }
                activity.runOnUiThread {
                    notifyItemRemoved(position)
                }
            }

            override fun onCounterRenamed(oldName: String, newName: String) {
                val position = counters.indexOf(oldName)
                counters[position] = newName
                if (currentSelectedCounterName == oldName) {
                    currentSelectedCounterName = newName
                    listObserver.onSelectedItemUpdated(position, viewModel.getCounterSummary(newName).value!!)
                }
                activity.runOnUiThread {
                    notifyItemChanged(position, Unit)
                }
            }

            override fun onCounterDecremented(counterName: String, oldEntryDate: Date) {
                Snackbar.make(
                    recyclerView!!,
                    activity.getString(R.string.decreased_entry, counterName),
                    Snackbar.LENGTH_LONG
                )
                    .setAction(R.string.undo) {
                        viewModel.incrementCounter(counterName, oldEntryDate)
                    }
                    .show()
            }
        })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val binding = FragmentEntryBinding.inflate(inflater, parent, false)
        val holder = EntryViewHolder(activity, binding, viewModel, touchHelper) { counter ->
            currentSelectedCounterName = counter.name
            listObserver.onItemSelected(counters.indexOf(counter.name), counter)
        }
        return holder
    }

    override fun onViewAttachedToWindow(holder: EntryViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (counters.size > 1 && !viewModel.isTutorialShown(Tutorial.DRAG)) {
            viewModel.setTutorialShown(Tutorial.DRAG)
            showDragTutorial(holder)
        }
    }

    fun showDragTutorial(holder: EntryViewHolder, onDismissListener: OnDismissListener? = null) {
        SimpleTooltip.Builder(activity)
            .anchorView(holder.binding.countText)
            .text(R.string.tutorial_drag)
            .gravity(Gravity.BOTTOM)
            .animated(true)
            .focusable(true) // modal requires focusable
            .modal(true)
            .onDismissListener(onDismissListener)
            .build()
            .show()
    }

    fun showPickDateTutorial(holder: EntryViewHolder, onDismissListener: OnDismissListener? = null) {
        holder.showPickDateTutorial(onDismissListener)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        val counter = viewModel.getCounterSummary(counters[position]).value
        if (counter != null) {
            holder.onBind(counter)
        } else {
            Log.d(TAG, "Counter not found or still loading at pos $position")
        }
    }

    override fun onMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(counters, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(counters, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onDragStart(viewHolder: RecyclerView.ViewHolder?) {
        // nothing to do
    }

    override fun onDragEnd(viewHolder: RecyclerView.ViewHolder?) {
        viewModel.saveCounterOrder(counters)
    }

    fun selectItem(position: Int) {
        val counterName = counters[position]
        viewModel.getCounterSummary(counterName).value?.let { counter ->
            listObserver.onItemSelected(position, counter)
        }
    }

    fun getCounters(): List<CounterSummary> {
        val allCounters = viewModel.getCounterList()
        val groupCounters = allCounters.filter { viewModel.getCounterGroup(it) == groupId }
        counters = groupCounters.toMutableList()
        return groupCounters.mapNotNull { viewModel.getCounterSummary(it).value }
    }

    fun refreshCounters() {
        val allCounters = viewModel.getCounterList()
        val filteredCounters = allCounters.filter { 
            viewModel.getCounterGroup(it) == groupId 
        }
        
        counters.clear()
        counters.addAll(filteredCounters)
        
        notifyDataSetChanged()
        
        for (counterName in counters) {
            observeNewCounter(counterName)
        }
        
        Log.d(TAG, "分组 $groupId 刷新了 ${counters.size} 个计数器")
    }

    fun getPositionForCounter(counterName: String): Int {
        return counters.indexOf(counterName)
    }

    fun scrollToPosition(position: Int) {
        recyclerView?.scrollToPosition(position)
    }
}
