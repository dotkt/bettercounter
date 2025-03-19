package org.kde.bettercounter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.kde.bettercounter.databinding.PageGroupBinding
import org.kde.bettercounter.persistence.CounterSummary
import org.kde.bettercounter.persistence.Group
import java.util.HashMap

private const val TAG = "GroupPagerAdapter"

class GroupPagerAdapter(
    private val activity: AppCompatActivity,
    private val viewModel: ViewModel,
    private val onCounterSelected: (CounterSummary) -> Unit
) : RecyclerView.Adapter<GroupPagerAdapter.GroupViewHolder>() {

    private val adapters = HashMap<String, EntryListViewAdapter>()
    private var groups: List<Group> = emptyList()

    init {
        refreshGroups()
    }

    override fun getItemCount(): Int = groups.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = PageGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val groupId = getGroupId(position)
        val adapter = getOrCreateAdapter(groupId)
        
        holder.binding.groupRecyclerView.layoutManager = LinearLayoutManager(activity)
        holder.binding.groupRecyclerView.adapter = adapter
        
        // 确保数据刷新
        adapter.refreshCounters()
    }

    private fun getOrCreateAdapter(groupId: String): EntryListViewAdapter {
        return adapters[groupId] ?: createAdapterForGroup(groupId).also {
            adapters[groupId] = it
        }
    }

    fun refreshGroups() {
        groups = viewModel.getGroups()
        notifyDataSetChanged()
        
        // 刷新所有已创建的适配器
        for (adapter in adapters.values) {
            adapter.refreshCounters()
        }
        
        Log.d(TAG, "刷新分组：共${groups.size}个分组")
    }

    private fun createAdapterForGroup(groupId: String): EntryListViewAdapter {
        return EntryListViewAdapter(
            activity, 
            viewModel,
            object : EntryListViewAdapter.EntryListObserver {
                override fun onItemSelected(position: Int, counter: CounterSummary) {
                    onCounterSelected(counter)
                }
                
                override fun onSelectedItemUpdated(position: Int, counter: CounterSummary) {
                    // 处理选中项更新
                }
                
                override fun onItemAdded(position: Int) {
                    // 处理添加项
                }
            },
            groupId
        )
    }
    
    // 获取分组名称（用于TabLayout标签）
    fun getGroupName(position: Int): String {
        return if (position >= 0 && position < groups.size) {
            groups[position].name
        } else {
            "默认"
        }
    }
    
    // 获取分组ID
    fun getGroupId(position: Int): String {
        return if (position >= 0 && position < groups.size) {
            groups[position].id
        } else {
            "default" // 默认分组ID
        }
    }
    
    // 获取分组在列表中的位置
    fun getPositionForGroupId(groupId: String): Int {
        return groups.indexOfFirst { it.id == groupId }
    }
    
    // 获取指定分组的适配器
    fun getAdapter(groupId: String): EntryListViewAdapter? {
        return adapters[groupId]
    }
    
    class GroupViewHolder(val binding: PageGroupBinding) : RecyclerView.ViewHolder(binding.root)
} 