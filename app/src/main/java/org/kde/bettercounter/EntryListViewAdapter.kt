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
    private var originalCounters: MutableList<String> = mutableListOf() // 备份原始数据
    private var currentFilter: String =""
    private var currentCategory: String? = null // 当前分类
    
    // 多选模式
    private var isMultiSelectMode = false
    private val selectedCounters = mutableSetOf<String>()
    
    fun setMultiSelectMode(enabled: Boolean) {
        isMultiSelectMode = enabled
        if (!enabled) {
            selectedCounters.clear()
        }
        notifyDataSetChanged()
    }
    
    fun isMultiSelectMode(): Boolean = isMultiSelectMode
    
    fun getSelectedCounters(): Set<String> = selectedCounters.toSet()
    
    fun toggleSelection(counterName: String) {
        if (selectedCounters.contains(counterName)) {
            selectedCounters.remove(counterName)
        } else {
            selectedCounters.add(counterName)
        }
        // 找到该计数器在列表中的位置并更新
        val position = counters.indexOf(counterName)
        if (position != -1) {
            notifyItemChanged(position)
        }
        // 通知外部更新选中数量
        if (activity is org.kde.bettercounter.ui.MainActivity) {
            (activity as org.kde.bettercounter.ui.MainActivity).updateSelectedCount()
        }
    }
    
    fun clearSelection() {
        selectedCounters.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = counters.size

    private val touchHelper = ItemTouchHelper(DragAndSwipeTouchHelper(this))

    private var recyclerView: RecyclerView? = null

    private var currentTooltip: SimpleTooltip? = null

    override fun onAttachedToRecyclerView(view: RecyclerView) {
        recyclerView = view
        touchHelper.attachToRecyclerView(view)
        super.onAttachedToRecyclerView(view)
    }

    init {
        viewModel.observeCounterChange(object : ViewModel.CounterObserver {

            fun observeNewCounter(counterName: String) {
                viewModel.getCounterSummary(counterName).observe(activity) {
                    // 检查计数器是否在当前过滤后的列表中
                    val position = counters.indexOf(it.name)
                    if (position != -1) {
                        // 只在计数器可见时更新UI
                        notifyItemChanged(position, Unit)
                        if (currentSelectedCounterName == it.name) {
                            listObserver.onSelectedItemUpdated(position, it)
                        }
                    }
                    // 如果计数器不在过滤列表中（被搜索过滤掉了），不需要更新UI
                }
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onInitialCountersLoaded() {
                activity.runOnUiThread {
                    val allCounters = viewModel.getCounterList().toMutableList()
                    originalCounters = allCounters.toMutableList() // 备份原始数据
                    applyFilters() // 应用过滤器
                    for (counterName in allCounters) {
                        observeNewCounter(counterName)
                    }
                }
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onCounterAdded(counterName: String) {
                activity.runOnUiThread {
                    if (!originalCounters.contains(counterName)) {
                        originalCounters.add(counterName)
                        val oldSize = counters.size
                        applyFilters() // 应用过滤器，如果新计数器符合当前过滤条件，会自动显示
                        val newSize = counters.size
                        if (newSize > oldSize) {
                            // 新计数器被添加到显示列表
                            val position = counters.indexOf(counterName)
                            if (position != -1) {
                                listObserver.onItemAdded(position)
                            }
                        }
                    }
                    observeNewCounter(counterName)
                }
            }

            override fun onCounterRemoved(counterName: String) {
                activity.runOnUiThread {
                    originalCounters.remove(counterName)
                    if (currentSelectedCounterName == counterName) {
                        currentSelectedCounterName = null
                    }
                    applyFilters() // 重新应用过滤器
                }
            }

            override fun onCounterRenamed(oldName: String, newName: String) {
                // 先更新原始列表
                val originalPosition = originalCounters.indexOf(oldName)
                if (originalPosition != -1) {
                    originalCounters[originalPosition] = newName
                }
                
                // 检查旧名称是否在当前过滤后的列表中
                val position = counters.indexOf(oldName)
                if (position != -1) {
                    // 如果旧名称在过滤列表中，更新它
                    counters[position] = newName
                    if (currentSelectedCounterName == oldName) {
                        currentSelectedCounterName = newName
                        listObserver.onSelectedItemUpdated(position, viewModel.getCounterSummary(newName).value!!)
                    }
                    activity.runOnUiThread {
                        // passing a second parameter disables the disappear+appear animation
                        notifyItemChanged(position, Unit)
                    }
                } else {
                    // 如果旧名称不在过滤列表中，检查新名称是否应该出现在过滤列表中
                    if (currentSelectedCounterName == oldName) {
                        currentSelectedCounterName = newName
                    }
                    // 重新应用过滤，因为新名称可能匹配当前过滤条件
                    activity.runOnUiThread {
                        filterCounters(currentFilter)
                    }
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
        currentTooltip?.dismiss()
        currentTooltip = SimpleTooltip.Builder(activity)
            .anchorView(holder.binding.nameText)
            .text(R.string.tutorial_drag)
            .gravity(Gravity.BOTTOM)
            .animated(true)
            .focusable(true)
            .modal(true)
            .onDismissListener(OnDismissListener { p0 ->
                onDismissListener?.onDismiss(p0)
                currentTooltip = null
            })
            .build()
        currentTooltip?.show()
    }

    fun dismissTooltip() {
        currentTooltip?.dismiss()
        currentTooltip = null
    }

    fun showPickDateTutorial(holder: EntryViewHolder, onDismissListener: OnDismissListener? = null) {
        holder.showPickDateTutorial(onDismissListener)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        val counter = viewModel.getCounterSummary(counters[position]).value
        if (counter != null) {
            holder.onBind(counter, isMultiSelectMode, selectedCounters.contains(counter.name)) { counterName ->
                toggleSelection(counterName)
            }
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
        // Do not store individual movements, store the final result in `onDragEnd`
    }

    override fun onDragStart(viewHolder: RecyclerView.ViewHolder?) {
        // nothing to do
    }

    override fun onDragEnd(viewHolder: RecyclerView.ViewHolder?) {
        viewModel.saveCounterOrder(counters)
    }

    /**
     * 更新过滤后的数据
     */
    @SuppressLint("NotifyDataSetChanged")
    fun updateFilteredData(filteredCounters: List<String>) {
        counters.clear()
        counters.addAll(filteredCounters)
        notifyDataSetChanged()
    }

    /**
     * 根据搜索文本过滤计数器
     */
    fun filterCounters(searchText: String) {
        currentFilter = searchText
        applyFilters()
    }

    /**
     * 设置当前分类
     */
    fun setCategory(category: String?) {
        currentCategory = category
        applyFilters()
    }

    /**
     * 应用所有过滤器（搜索文本和分类）
     */
    private fun applyFilters() {
        var filteredCounters = originalCounters
        
        // 先按分类过滤
        if (currentCategory != null) {
            filteredCounters = filteredCounters.filter { counterName ->
                viewModel.getCounterCategory(counterName) == currentCategory
            }.toMutableList()
        }
        
        // 再按搜索文本过滤
        if (currentFilter.isNotEmpty()) {
            filteredCounters = filteredCounters.filter { counterName ->
                counterName.contains(currentFilter, ignoreCase = true)
            }.toMutableList()
        }
        
        updateFilteredData(filteredCounters)
    }

    fun selectItem(position: Int) {
        val counterName = counters[position]
        viewModel.getCounterSummary(counterName).value?.let { counter ->
            listObserver.onItemSelected(position, counter)
        }
    }

    /**
     * 根据计数器名称选择项目
     */
    fun selectItemByName(counterName: String) {
        val position = counters.indexOf(counterName)
        if (position != -1) {
            selectItem(position)
        }
    }
    
    /**
     * 获取计数器在列表中的位置
     */
    fun getItemPosition(counterName: String): Int {
        return counters.indexOf(counterName)
    }

}
