package org.kde.bettercounter.ui

import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.kde.bettercounter.EntryListViewAdapter
import org.kde.bettercounter.ViewModel

class CategoryPagerAdapter(
    private val activity: MainActivity,
    private val viewModel: ViewModel,
    private val listObserver: EntryListViewAdapter.EntryListObserver
) : RecyclerView.Adapter<CategoryPagerAdapter.CategoryViewHolder>() {

    private val categories: MutableList<String> = mutableListOf()
    private val adapters: MutableMap<String, EntryListViewAdapter> = mutableMapOf()
    private var isInitialized = false

    init {
        // 初始化时不调用 updateCategories，避免循环依赖
        // 由 MainActivity 在创建后手动调用
    }

    fun updateCategories() {
        val allCategories = viewModel.getAllCategories().sorted()
        categories.clear()
        // 只添加实际的分类，不添加"全部"页面
        allCategories.forEach { category ->
            if (!categories.contains(category)) {
                categories.add(category)
            }
        }
        notifyDataSetChanged()
        // 通知MainActivity更新分类导航栏（仅在已初始化后）
        if (isInitialized) {
            (activity as? MainActivity)?.onCategoriesUpdated()
        }
    }
    
    /**
     * 标记为已初始化，之后可以安全地调用 MainActivity 的方法
     * 注意：不立即更新分类，需要等待数据加载完成后再调用 updateCategories()
     */
    fun markInitialized() {
        isInitialized = true
        // 不在这里调用 updateCategories()，等待数据加载完成后再调用
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val recyclerView = RecyclerView(parent.context).apply {
            layoutManager = LinearLayoutManager(parent.context)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return CategoryViewHolder(recyclerView)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.bind(category)
    }

    override fun getItemCount(): Int = categories.size

    fun getCategoryAt(position: Int): String {
        return categories[position]
    }

    fun findPositionForCategory(category: String): Int {
        val index = categories.indexOf(category)
        return if (index == -1) 0 else index
    }

    fun getAdapterForCategory(category: String): EntryListViewAdapter? {
        return adapters[category]
    }

    fun ensureAdapterForCategory(category: String): EntryListViewAdapter {
        val key = category
        val adapter = adapters[key]
        return if (adapter != null) {
            adapter
        } else {
            val newAdapter = EntryListViewAdapter(activity, viewModel, listObserver).apply {
                setCategory(category)
            }
            adapters[key] = newAdapter
            newAdapter
        }
    }

    fun getAllCategoriesForLog(): List<String> {
        return categories.toList()
    }
    
    /**
     * 为所有适配器设置多选模式
     */
    fun setMultiSelectModeForAll(enabled: Boolean) {
        adapters.values.forEach { adapter ->
            adapter.setMultiSelectMode(enabled)
        }
    }
    
    /**
     * 获取所有适配器中选中的计数器总数
     */
    fun getTotalSelectedCount(): Int {
        return adapters.values.sumOf { adapter ->
            adapter.getSelectedCounters().size
        }
    }
    
    /**
     * 获取所有适配器中选中的计数器
     */
    fun getAllSelectedCounters(): Set<String> {
        val allSelected = mutableSetOf<String>()
        adapters.values.forEach { adapter ->
            allSelected.addAll(adapter.getSelectedCounters())
        }
        return allSelected
    }

    inner class CategoryViewHolder(val recyclerView: RecyclerView) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(recyclerView) {

        private var currentCategory: String? = null
        private var adapter: EntryListViewAdapter? = null

        fun bind(category: String) {
            currentCategory = category

            // 如果已经有适配器，直接使用
            adapter = adapters[category]
            if (adapter == null) {
                // 创建新的适配器
                adapter = EntryListViewAdapter(activity, viewModel, listObserver).apply {
                    setCategory(category)
                }
                adapters[category] = adapter!!
            } else {
                // 更新现有适配器的分类
                adapter!!.setCategory(category)
            }

            recyclerView.adapter = adapter
        }
    }
}
