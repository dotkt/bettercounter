package org.kde.bettercounter.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.kde.bettercounter.EntryListViewAdapter
import org.kde.bettercounter.R
import org.kde.bettercounter.ViewModel
import org.kde.bettercounter.persistence.CounterSummary
import org.kde.bettercounter.persistence.Group

class GroupPagerAdapter(
    private val activity: AppCompatActivity,
    private val viewModel: ViewModel
) : RecyclerView.Adapter<GroupPagerAdapter.GroupViewHolder>() {

    private var groups: List<Group> = viewModel.getGroups()
    private val adapters = mutableMapOf<String, EntryListViewAdapter>()

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val recyclerView: RecyclerView = itemView.findViewById(R.id.group_recycler_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_group_page, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        
        if (!adapters.containsKey(group.id)) {
            val adapter = EntryListViewAdapter(
                activity,
                viewModel,
                object : EntryListViewAdapter.EntryListObserver {
                    override fun onItemSelected(position: Int, counter: CounterSummary) {
                        // 实现必要的回调...
                    }
                    
                    override fun onSelectedItemUpdated(position: Int, counter: CounterSummary) {
                        // 实现必要的回调...
                    }
                    
                    override fun onItemAdded(position: Int) {
                        // 实现必要的回调...
                    }
                },
                group.id
            )
            adapters[group.id] = adapter
        }
        
        val entryAdapter = adapters[group.id]!!
        entryAdapter.refreshCounters()
        
        holder.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = entryAdapter
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    override fun getItemCount(): Int = groups.size

    fun getGroupName(position: Int): String {
        return if (position >= 0 && position < groups.size) {
            groups[position].name
        } else {
            ""
        }
    }
    
    fun refreshGroups() {
        groups = viewModel.getGroups()
        notifyDataSetChanged()
    }
} 