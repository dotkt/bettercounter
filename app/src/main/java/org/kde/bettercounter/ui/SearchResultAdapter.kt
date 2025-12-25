package org.kde.bettercounter.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.kde.bettercounter.R
import org.kde.bettercounter.ViewModel

class SearchResultAdapter(
    private val viewModel: ViewModel,
    private val onItemClick: (String, String) -> Unit // (counterName, category)
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CATEGORY_HEADER = 0
        private const val TYPE_COUNTER_ITEM = 1
    }

    data class SearchResultItem(
        val type: Int,
        val category: String? = null,
        val counterName: String? = null
    )

    private val items = mutableListOf<SearchResultItem>()

    fun updateResults(results: Map<String, List<String>>) {
        items.clear()
        results.forEach { (category, counters) ->
            // 添加分类标题
            items.add(SearchResultItem(TYPE_CATEGORY_HEADER, category = category))
            // 添加该分类下的计数器
            counters.forEach { counterName ->
                items.add(SearchResultItem(TYPE_COUNTER_ITEM, category = category, counterName = counterName))
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return items[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_CATEGORY_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_search_category_header, parent, false)
                CategoryHeaderViewHolder(view)
            }
            TYPE_COUNTER_ITEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_search_result, parent, false)
                CounterItemViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is CategoryHeaderViewHolder -> {
                holder.bind(item.category ?: "默认")
            }
            is CounterItemViewHolder -> {
                holder.bind(item.counterName ?: "", item.category ?: "默认")
            }
        }
    }

    override fun getItemCount(): Int = items.size

    inner class CategoryHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(category: String) {
            (itemView as TextView).text = "分类: $category"
        }
    }

    inner class CounterItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val counterNameText: TextView = itemView.findViewById(R.id.counterName)
        private val categoryText: TextView = itemView.findViewById(R.id.categoryName)

        fun bind(counterName: String, category: String) {
            counterNameText.text = counterName
            categoryText.text = "分类: $category"
            
            itemView.setOnClickListener {
                val item = items[adapterPosition]
                item.category?.let { cat ->
                    item.counterName?.let { name ->
                        onItemClick(name, cat)
                    }
                }
            }
        }
    }
}

