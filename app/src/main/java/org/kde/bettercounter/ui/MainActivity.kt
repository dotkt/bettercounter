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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        handleIntent(intent)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置Toolbar为ActionBar
        setSupportActionBar(binding.toolbar)

        viewModel = (application as BetterApplication).viewModel

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
        
        groupPagerAdapter = GroupPagerAdapter(this, viewModel)
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
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        Log.d(TAG, "handleIntent called with intent: $intent")
        intent?.extras?.let { bundle ->
            for (key in bundle.keySet()) {
                Log.d(TAG, "Extra: $key = ${bundle.get(key)}")
            }
        }
        
        intent?.getStringExtra(EXTRA_COUNTER_NAME)?.let { counterName ->
            Log.d(TAG, "Got counter name: $counterName")
            // 等待数据加载完成后再滚动
            viewModel.observeCounterChange(object : ViewModel.CounterObserver {
                override fun onInitialCountersLoaded() {
                    val position = viewModel.getCounterList().indexOf(counterName)
                    Log.d(TAG, "Counter position: $position")
                    if (position != -1) {
                        // 使用 post 确保在布局完成后执行
                        binding.recycler.post {
                            binding.recycler.smoothScrollToPosition(position)
                            // 自动展开详情面板
                            entryViewAdapter.selectItem(position)
                        }
                    }
                    viewModel.removeCounterChangeObserver(this)
                }
                
                // 实现其他必需的接口方法
                override fun onCounterAdded(counterName: String) {}
                override fun onCounterRemoved(counterName: String) {}
                override fun onCounterRenamed(oldName: String, newName: String) {}
                override fun onCounterDecremented(counterName: String, oldEntryDate: Date) {}
            })
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

            CounterSettingsDialogBuilder(this@MainActivity, viewModel)
                .forExistingCounter(counter)
                .setOnSaveListener { newCounterMetadata ->
                    if (counter.name != newCounterMetadata.name) {
                        viewModel.editCounter(counter.name, newCounterMetadata)
                    } else {
                        viewModel.editCounterSameName(newCounterMetadata)
                    }
                    // We are not subscribed to the summary livedata, so we won't get notified of the change we just made.
                    // Update our local copy so it has the right data if we open the dialog again.
                    counter.name = newCounterMetadata.name
                    counter.interval = newCounterMetadata.interval
                    counter.color = newCounterMetadata.color
                }
                .setOnDismissListener {
                    binding.fab.visibility = View.VISIBLE
                }
                .setOnDeleteListener { _, _ ->
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
                .show()
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
}
