package org.kde.bettercounter.ui

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.github.douglasjunior.androidSimpleTooltip.SimpleTooltip
import kotlinx.coroutines.Dispatchers as KotlinDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.ChartsAdapter
import org.kde.bettercounter.EntryListViewAdapter
import org.kde.bettercounter.R
import org.kde.bettercounter.ViewModel
import org.kde.bettercounter.boilerplate.CreateFileParams
import org.kde.bettercounter.boilerplate.CreateFileResultContract
import org.kde.bettercounter.boilerplate.OpenFileParams
import org.kde.bettercounter.boilerplate.OpenFileResultContract
import org.kde.bettercounter.databinding.ActivityMainBinding
import org.kde.bettercounter.databinding.ProgressDialogBinding
import org.kde.bettercounter.persistence.CounterSummary
import org.kde.bettercounter.persistence.Interval
import org.kde.bettercounter.persistence.Tutorial
import org.kde.bettercounter.persistence.Group
import android.widget.EditText
import android.widget.PopupMenu
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.CoroutineScope
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Date

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_COUNTER_NAME = "EXTRA_COUNTER_NAME"
        private const val TAG = "MainActivity"
        const val REQUEST_EDIT_COUNTER = 1001
    }

    private lateinit var viewModel: ViewModel
    private lateinit var entryViewAdapter: EntryListViewAdapter
    private lateinit var binding: ActivityMainBinding
    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>
    private var intervalOverride: Interval? = null
    private var sheetIsExpanding = false
    private var onBackPressedCloseSheetCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                this.isEnabled = false
                sheetIsExpanding = false
            }
        }
    }
    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var groupPagerAdapter: GroupPagerAdapter
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var currentCounter: CounterSummary
    private lateinit var chartsAdapter: ChartsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置Toolbar为ActionBar
        setSupportActionBar(binding.toolbar)

        // 先初始化viewModel
        viewModel = (application as BetterApplication).viewModel
        
        // 然后再处理Intent
        handleIntent(intent)
        
        // Bottom sheet with graph
        // -----------------------
        sheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        sheetIsExpanding = false
        val sheetFoldedPadding = binding.recycler.paddingBottom // padding so the fab is in view
        var sheetUnfoldedPadding = 0 // padding to fit the bottomSheet. We read it once and assume all sheets are going to be the same height
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                sheetUnfoldedPadding = binding.bottomSheet.height + 50
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        sheetBehavior.addBottomSheetCallback(object : BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    onBackPressedCloseSheetCallback.isEnabled = true
                    sheetIsExpanding = false
                    if (!viewModel.isTutorialShown(Tutorial.CHANGE_GRAPH_INTERVAL)) {
                        viewModel.setTutorialShown(Tutorial.CHANGE_GRAPH_INTERVAL)
                        showChangeGraphIntervalTutorial()
                    }
                } else if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    onBackPressedCloseSheetCallback.isEnabled = false
                    setFabToCreate()
                    entryViewAdapter.clearItemSelected()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (!sheetIsExpanding) { // only do this when collapsing. when expanding we set the final padding at once so smoothScrollToPosition can do its job
                    val bottomPadding =
                        sheetFoldedPadding + ((1.0 + slideOffset) * (sheetUnfoldedPadding - sheetFoldedPadding)).toInt()
                    binding.recycler.setPadding(0, 0, 0, bottomPadding)
                }
            }
        })

        onBackPressedDispatcher.addCallback(onBackPressedCloseSheetCallback)

        // Create counter dialog
        // ---------------------
        setFabToCreate()

        // Counter list
        // ------------
        entryViewAdapter = EntryListViewAdapter(this, viewModel, object : EntryListViewAdapter.EntryListObserver {
            override fun onItemAdded(position: Int) {
                binding.recycler.smoothScrollToPosition(position)
            }
            override fun onSelectedItemUpdated(position: Int, counter: CounterSummary) {
                binding.detailsTitle.text = counter.name
                val interval = intervalOverride ?: counter.interval.toChartDisplayableInterval()
                val adapter = ChartsAdapter(this@MainActivity, viewModel, counter, interval,
                    onIntervalChange = { newInterval ->
                        intervalOverride = newInterval
                        onSelectedItemUpdated(position, counter)
                    },
                    onDateChange = { newDate ->
                        val chartPosition = findPositionForRangeStart(newDate)
                        binding.charts.scrollToPosition(chartPosition)
                    },
                    onDataDisplayed = {
                        // Re-triggers calculating the expanded offset, since the height of the sheet
                        // contents depend on whether the stats take one or two lines of text
                        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                )
                binding.charts.swapAdapter(adapter, true)
                binding.charts.scrollToPosition(adapter.itemCount - 1) // Select the latest chart
            }
            override fun onItemSelected(position: Int, counter: CounterSummary) {
                if (sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                    sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    onBackPressedCloseSheetCallback.isEnabled = true
                    sheetIsExpanding = true
                }
                binding.recycler.setPadding(0, 0, 0, sheetUnfoldedPadding)
                binding.recycler.smoothScrollToPosition(position)

                setFabToEdit(counter)

                intervalOverride = null

                onSelectedItemUpdated(position, counter)
            }
        })
        binding.recycler.adapter = entryViewAdapter
        binding.recycler.layoutManager = LinearLayoutManager(this)

        binding.charts.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false).apply {
            stackFromEnd = true
        }

        binding.charts.isNestedScrollingEnabled = false
        PagerSnapHelper().attachToRecyclerView(binding.charts) // Scroll one by one

        // For some reason, when the app is installed via Android Studio, the broadcast that
        // refreshes the widgets after installing doesn't trigger. Do it manually here.
        forceRefreshWidgets(this)

        startRefreshEveryHourBoundary()

        // 设置分组视图
        viewPager = binding.viewPager
        tabLayout = binding.tabLayout
        
        groupPagerAdapter = GroupPagerAdapter(
            this, 
            viewModel,
            { counter -> 
                // 处理计数器选择事件
                currentCounter = counter
                displayCounterDetails(counter)
            }
        )
        viewPager.adapter = groupPagerAdapter
        
        // 连接TabLayout和ViewPager
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = groupPagerAdapter.getGroupName(position)
        }.attach()
        
        // 设置Tab长按事件
        setupTabLongClickListener()

        // 设置分组按钮点击监听
        binding.addGroupFab.setOnClickListener {
            showAddGroupDialog()
        }

        // 每次添加新分组后刷新适配器
        groupPagerAdapter.refreshGroups()

        setupDetailsTitle()

        // 在onCreate方法中保存FAB原始点击监听器
        val fabOriginalClickListener = View.OnClickListener {
            val builder = CounterSettingsDialogBuilder(this, viewModel)
                .forNewCounter()
                .setOnSaveListener { metadata ->
                    viewModel.addCounter(metadata)
                }
                .setOnDismissListener {
                    binding.fab.visibility = View.VISIBLE
                }
            builder.show()
            binding.fab.visibility = View.INVISIBLE
        }
        
        // 保存原始点击监听器以便后续恢复
        binding.fab.setTag(R.id.fab_original_listener, fabOriginalClickListener)
        binding.fab.setOnClickListener(fabOriginalClickListener)

        // 添加页面切换监听器
        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // 获取当前分组ID
                val groups = viewModel.getGroups()
                val currentGroupId = if (position >= 0 && position < groups.size) {
                    groups[position].id
                } else {
                    "default"
                }
                
                // 刷新当前分组的适配器
                groupPagerAdapter.getAdapter(currentGroupId)?.refreshCounters()
                
                // 记录日志，便于调试
                Log.d(TAG, "切换到分组位置: $position, 分组ID: $currentGroupId")
            }
        })
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        // 确保viewModel已初始化
        if (!::viewModel.isInitialized) return
        
        val counterName = intent.getStringExtra(EXTRA_COUNTER_NAME)
        if (counterName != null) {
            Log.d(TAG, "从Intent中获取计数器名称: $counterName")
            // 使用Handler延迟执行，确保Activity完全初始化
            handler.postDelayed({
                navigateToCounter(counterName)
            }, 500)
        }
    }

    private fun millisecondsUntilNextHour(): Long {
        val current = LocalDateTime.now()
        val nextHour = current.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        return ChronoUnit.MILLIS.between(current, nextHour)
    }

    private fun startRefreshEveryHourBoundary() {
        lifecycleScope.launch(KotlinDispatchers.IO) {
            var lastDate = LocalDateTime.now().toLocalDate()
            
            while (isActive) {
                delay(millisecondsUntilNextHour())
                
                // 检查日期是否变化
                val currentDate = LocalDateTime.now().toLocalDate()
                if (currentDate != lastDate) {
                    // 日期已变化，刷新所有计数器
                    Log.d(TAG, "Date changed from $lastDate to $currentDate, refreshing counters")
                    lastDate = currentDate
                }
                
                // 刷新所有观察者
                viewModel.refreshAllObservers()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    fun showChangeGraphIntervalTutorial(onDismissListener: SimpleTooltip.OnDismissListener? = null) {
        val adapter = binding.charts.adapter!!
        binding.charts.scrollToPosition(adapter.itemCount - 1)
        val holder = binding.charts.findViewHolderForAdapterPosition(adapter.itemCount - 1) as ChartHolder
        holder.showChangeGraphIntervalTutorial(onDismissListener)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.export_csv -> {
                try {
                    val fileName = "bettercounter_export.csv"
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                    }

                    val resolver = applicationContext.contentResolver
                    val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Files.getContentUri("external")
                    }

                    // 删除可能存在的同名文件
                    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                    val selectionArgs = arrayOf(fileName)
                    resolver.delete(uri, selection, selectionArgs)

                    // 创建新文件
                    val itemUri = resolver.insert(uri, values)
                    if (itemUri != null) {
                        val outputStream = resolver.openOutputStream(itemUri)
                        if (outputStream != null) {
                            val progressHandler = Handler(Looper.getMainLooper()) {
                                if (it.arg1 == it.arg2) {
                                    Snackbar.make(binding.recycler, "已导出到下载目录: $fileName", Snackbar.LENGTH_LONG).show()
                                }
                                true
                            }
                            viewModel.exportAll(outputStream, progressHandler)
                        } else {
                            throw Exception("无法打开输出流")
                        }
                    } else {
                        throw Exception("无法创建文件")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to export: ${e.message}")
                    Snackbar.make(binding.recycler, "导出失败: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
                true
            }
            R.id.import_csv -> {
                importFilePicker.launch(OpenFileParams("text/*"))
                true
            }
            R.id.show_tutorial -> {
                if (viewModel.getCounterList().isEmpty()) {
                    Snackbar.make(binding.recycler, getString(R.string.no_counters), Snackbar.LENGTH_LONG).show()
                } else {
                    binding.recycler.scrollToPosition(0)
                    val holder = binding.recycler.findViewHolderForAdapterPosition(0) as EntryViewHolder
                    entryViewAdapter.showDragTutorial(holder) {
                        entryViewAdapter.showPickDateTutorial(holder) {
                            viewModel.resetTutorialShown(Tutorial.CHANGE_GRAPH_INTERVAL)
                            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                        }
                    }
                }
                true
            }
            R.id.add_group -> {
                showAddGroupDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val importFilePicker: ActivityResultLauncher<OpenFileParams> = registerForActivityResult(
        OpenFileResultContract()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.openInputStream(uri)?.let { stream ->

                var hasImported = false

                val progressDialogBinding = ProgressDialogBinding.inflate(layoutInflater)
                val dialog = MaterialAlertDialogBuilder(this)
                    .setView(progressDialogBinding.root)
                    .setCancelable(false)
                    .setOnDismissListener {
                        if (hasImported) {
                            // restart app
                            val intent = Intent(this, MainActivity::class.java)
                            this.startActivity(intent)
                            finishAffinity()
                        }
                    }
                    .create()
                dialog.show()

                val progressHandler = Handler(Looper.getMainLooper()) {
                    if (it.arg2 == -1) {
                        progressDialogBinding.text.text = getString(R.string.import_error)
                        dialog.setCancelable(true)
                    } else {
                        progressDialogBinding.text.text = getString(R.string.imported_n, it.arg1)
                        if (it.arg2 == 1) { // we are done
                            dialog.setCancelable(true)
                            hasImported = true
                            // Hide all tutorials
                            Tutorial.entries.forEach { tuto -> viewModel.setTutorialShown(tuto) }
                        }
                    }
                    true
                }
                viewModel.importAll(this, stream, progressHandler)
            }
        }
    }

    private fun setFabToCreate() {
        binding.fab.setImageResource(R.drawable.ic_add)
        binding.fab.setOnClickListener {
            binding.fab.visibility = View.GONE
            CounterSettingsDialogBuilder(this@MainActivity, viewModel)
                .forNewCounter()
                .setOnSaveListener { counterMetadata ->
                    viewModel.addCounter(counterMetadata)
                    switchToGroup(counterMetadata.groupId)
                }
                .setOnDismissListener { binding.fab.visibility = View.VISIBLE }
                .show()
        }
    }

    private fun setFabToEdit(counter: CounterSummary) {
        binding.fab.setImageResource(R.drawable.ic_edit)
        binding.fab.setOnClickListener {
            binding.fab.visibility = View.GONE

            // 保存当前FAB的原始点击行为
            val originalClickListener = binding.fab.getTag(R.id.fab_original_listener) as? View.OnClickListener
            
            // 记录原始分组ID
            val originalGroupId = counter.groupId
            
            // 打开编辑对话框
            val builder = CounterSettingsDialogBuilder(this, viewModel)
                .forExistingCounter(counter)
                .setOnSaveListener { newCounterMetadata ->
                    if (counter.name != newCounterMetadata.name) {
                        viewModel.editCounter(counter.name, newCounterMetadata)
                    } else {
                        viewModel.editCounterSameName(newCounterMetadata)
                    }
                    
                    // 如果分组发生变化，更新UI
                    if (originalGroupId != newCounterMetadata.groupId) {
                        Log.d(TAG, "计数器分组已变更: ${counter.name} 从 $originalGroupId 到 ${newCounterMetadata.groupId}")
                        
                        // 刷新两个分组的适配器
                        groupPagerAdapter.getAdapter(originalGroupId)?.refreshCounters()
                        groupPagerAdapter.getAdapter(newCounterMetadata.groupId)?.refreshCounters()
                        
                        // 如果当前显示的是原分组，切换到新分组
                        if (viewPager.currentItem == groupPagerAdapter.getPositionForGroupId(originalGroupId)) {
                            val newPosition = groupPagerAdapter.getPositionForGroupId(newCounterMetadata.groupId)
                            if (newPosition >= 0) {
                                viewPager.currentItem = newPosition
                            }
                        }
                    }
                    
                    // 刷新详情页面
                    counter.groupId = newCounterMetadata.groupId
                    setupChartsForCounter(counter)
                }
                .setOnDeleteListener { _, _ ->
                    showDeleteDialog(counter)
                }
                .setOnDismissListener {
                    // 恢复FAB的原始功能
                    binding.fab.setImageResource(R.drawable.ic_add)
                    binding.fab.setOnClickListener(originalClickListener)
                }
            builder.show()
        }
    }

    // 添加分组管理相关方法
    private fun setupTabLongClickListener() {
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i)
            tab?.view?.setOnLongClickListener {
                val group = viewModel.getGroups()[i]
                if (group.id != "default") { // 默认分组不允许修改
                    showGroupOptionsMenu(it, group)
                }
                true
            }
        }
    }

    private fun showGroupOptionsMenu(view: View, group: Group) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.group_options, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.rename_group -> {
                    showRenameGroupDialog(group)
                    true
                }
                R.id.delete_group -> {
                    showDeleteGroupDialog(group)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRenameGroupDialog(group: Group) {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(R.string.rename_group)
        
        val input = EditText(this)
        input.setText(group.name)
        builder.setView(input)
        
        builder.setPositiveButton(R.string.save) { _, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                viewModel.renameGroup(group.id, newName)
                groupPagerAdapter.refreshGroups()
            }
        }
        
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

    private fun showDeleteGroupDialog(group: Group) {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(R.string.delete_group)
        builder.setMessage(getString(R.string.delete_group_confirmation, group.name))
        
        builder.setPositiveButton(R.string.delete) { _, _ ->
            viewModel.deleteGroup(group.id)
            groupPagerAdapter.refreshGroups()
        }
        
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

    // 添加创建新分组的方法
    private fun showAddGroupDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(R.string.add_group)
        
        val input = EditText(this)
        input.hint = getString(R.string.group_name)
        builder.setView(input)
        
        builder.setPositiveButton(R.string.save) { _, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                viewModel.addGroup(name)
                groupPagerAdapter.refreshGroups()
            }
        }
        
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

    // 添加计数器后切换到对应分组
    private fun switchToGroup(groupId: String) {
        val groups = viewModel.getGroups()
        val index = groups.indexOfFirst { it.id == groupId }
        if (index >= 0) {
            viewPager.currentItem = index
        }
    }

    // 添加导航到指定计数器的方法
    fun navigateToCounter(counterName: String) {
        Log.d(TAG, "尝试导航到计数器: $counterName")
        
        // 获取计数器所在分组
        val groupId = viewModel.getCounterGroup(counterName)
        Log.d(TAG, "计数器所在分组ID: $groupId")
        
        // 首先关闭详情面板
        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        
        // 切换到对应分组标签
        val groups = viewModel.getGroups()
        val tabIndex = groups.indexOfFirst { it.id == groupId }
        Log.d(TAG, "分组索引: $tabIndex")
        
        if (tabIndex >= 0) {
            // 切换标签页
            viewPager.currentItem = tabIndex
            
            // 使用延迟确保视图已经切换完成
            handler.postDelayed({
                // 获取当前显示的分组适配器
                val adapter = groupPagerAdapter.getAdapter(groupId)
                
                if (adapter != null) {
                    Log.d(TAG, "找到分组适配器")
                    
                    // 确保数据已加载
                    adapter.refreshCounters()
                    
                    // 查找计数器位置
                    val position = adapter.getPositionForCounter(counterName)
                    Log.d(TAG, "计数器在列表中的位置: $position")
                    
                    if (position >= 0) {
                        // 滚动到并选中计数器
                        adapter.selectItem(position)
                        adapter.scrollToPosition(position)
                        
                        // 如果仍有问题，再尝试一次延迟滚动
                        handler.postDelayed({
                            adapter.scrollToPosition(position)
                        }, 200)
                    }
                } else {
                    Log.d(TAG, "未找到分组适配器")
                }
            }, 300) // 等待标签页切换完成
        }
    }

    private fun setupDetailsTitle() {
        binding.detailsTitle.setOnClickListener {
            val counterName = binding.detailsTitle.text.toString()
            Log.d(TAG, "点击了详情标题: $counterName")
            if (counterName.isNotEmpty()) {
                navigateToCounter(counterName)
            }
        }
    }

    private fun displayCounterDetails(counter: CounterSummary) {
        currentCounter = counter
        binding.detailsTitle.text = counter.name
        
        // 设置图表
        setupChartsForCounter(counter)
        
        // 修改FAB按钮的行为 - 当详情页面打开时点击FAB编辑当前计数器
        binding.fab.setImageResource(R.drawable.ic_edit) // 可以换成编辑图标
        binding.fab.setOnClickListener {
            // 保存当前FAB的原始点击行为
            val originalClickListener = binding.fab.getTag(R.id.fab_original_listener) as? View.OnClickListener
            
            // 打开编辑对话框
            val builder = CounterSettingsDialogBuilder(this, viewModel)
                .forExistingCounter(counter)
                .setOnSaveListener { newCounterMetadata ->
                    if (counter.name != newCounterMetadata.name) {
                        viewModel.editCounter(counter.name, newCounterMetadata)
                    } else {
                        viewModel.editCounterSameName(newCounterMetadata)
                    }
                    // 刷新详情页面
                    setupChartsForCounter(counter)
                }
                .setOnDeleteListener { _, _ ->
                    showDeleteDialog(counter)
                }
                .setOnDismissListener {
                    // 恢复FAB的原始功能
                    binding.fab.setImageResource(R.drawable.ic_add)
                    binding.fab.setOnClickListener(originalClickListener)
                }
            builder.show()
        }
        
        // 展开底部详情面板
        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheetIsExpanding = true
        binding.recycler.setPadding(0, 0, 0, binding.bottomSheet.height + 50)
        
        // 设置回退键监听以关闭详情面板
        onBackPressedCloseSheetCallback.isEnabled = true
    }

    private fun setupChartsForCounter(counter: CounterSummary) {
        try {
            // 使用完整参数创建ChartsAdapter
            chartsAdapter = ChartsAdapter(
                this,
                viewModel, 
                counter,
                interval = intervalOverride ?: counter.interval,
                onIntervalChange = { newInterval ->
                    // 保存新的间隔设置
                    intervalOverride = newInterval
                    // 刷新图表
                    setupChartsForCounter(counter)
                },
                onDateChange = { /* 日期变更处理 */ },
                onDataDisplayed = { /* 数据显示后的处理 */ }
            )
            binding.charts.adapter = chartsAdapter
        } catch (e: UnsupportedOperationException) {
            // 处理无法显示图表的情况
            Log.e(TAG, "无法为计数器 ${counter.name} 创建图表: ${e.message}")
            
            // 显示一个简单的错误信息
            binding.charts.adapter = null
            Snackbar.make(
                binding.root,
                "无法显示当前计数器的图表统计",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun showDeleteDialog(counter: CounterSummary) {
        MaterialAlertDialogBuilder(this)
            .setTitle(counter.name)
            .setMessage(R.string.delete_confirmation)
            .setNeutralButton(R.string.reset) { _, _ ->
                viewModel.resetCounter(counter.name)
                sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                onBackPressedCloseSheetCallback.isEnabled = false
                sheetIsExpanding = false
            }
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteCounter(counter.name)
                sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                onBackPressedCloseSheetCallback.isEnabled = false
                sheetIsExpanding = false
                removeWidgets(this, counter.name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
