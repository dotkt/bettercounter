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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Date
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast

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

    // 在类级别声明权限请求码
    private val STORAGE_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        handleIntent(intent)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
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

        startRefreshEveryMinuteBoundary()
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
                        binding.recycler.post {
                            // 先滚到最下面
                            binding.recycler.scrollToPosition(entryViewAdapter.itemCount - 1)
                            // 再 post 一次，等 layout 完成后滚回目标
                            binding.recycler.post {
                                entryViewAdapter.selectItem(position) // 展开详情面板
                                binding.recycler.scrollToPosition(position)
                            }
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

    private fun startRefreshEveryMinuteBoundary() {
        lifecycleScope.launch(Dispatchers.IO) {
            var lastDate = LocalDateTime.now().toLocalDate()
            while (isActive) {
                delay(60 * 1000) // 每分钟检测一次
                val currentDate = LocalDateTime.now().toLocalDate()
                if (currentDate != lastDate) {
                    viewModel.refreshAllObservers() // 只在IO线程调用
                    lastDate = currentDate
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main, menu)
        // 添加测试菜单项
        menu.add(Menu.NONE, 999, Menu.NONE, "测试日期变化")
        return true
    }

    fun showChangeGraphIntervalTutorial(onDismissListener: SimpleTooltip.OnDismissListener? = null) {
        val adapter = binding.charts.adapter!!
        binding.charts.scrollToPosition(adapter.itemCount - 1)
        val holder = binding.charts.findViewHolderForAdapterPosition(adapter.itemCount - 1) as ChartHolder
        holder.showChangeGraphIntervalTutorial(onDismissListener)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.export_csv -> {
                // 检查并请求权限，然后导出
                checkPermissionAndExport()
            }
            R.id.import_csv -> {
                importFilePicker.launch(OpenFileParams("text/*"))
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
            }
            999 -> {
                // 测试日期变化
                lifecycleScope.launch(Dispatchers.IO) {
                    Log.d(TAG, "手动触发日期变化测试")
                    viewModel.refreshAllObservers()
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.recycler, "已触发计数器刷新", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
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

    // 添加权限请求相关方法
    private fun checkPermissionAndExport() {
        Log.d(TAG, "检查导出权限")
        // 对于Android 10及以上版本，需要特殊处理
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // 已有权限，直接导出
                exportToDownloads()
            } else {
                // 引导用户到设置页面授予权限
                try {
                    Log.d(TAG, "请求完整存储权限")
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivityForResult(intent, STORAGE_PERMISSION_CODE)
                    Toast.makeText(this, "请在设置中授予存储权限", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(TAG, "打开权限设置页面失败: ${e.message}")
                    // 如果Intent不可用，尝试回退到旧版权限请求
                    if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.Q) {
                        requestLegacyStoragePermission()
                    } else {
                        Snackbar.make(binding.recycler, "无法请求权限，请在系统设置中手动授予", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            // Android 9及以下版本使用传统权限请求
            requestLegacyStoragePermission()
        }
    }

    private fun requestLegacyStoragePermission() {
        Log.d(TAG, "强制请求存储权限")
        
        // 不检查是否已经有权限，直接请求
        // 这将确保始终显示权限对话框（如果系统允许）
        requestPermissions(
            arrayOf(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            STORAGE_PERMISSION_CODE
        )
    }

    // 处理权限请求结果
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // 权限已授予，执行导出
                exportToDownloads()
            } else {
                Snackbar.make(binding.recycler, "需要存储权限才能导出文件", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    // 处理Activity结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // 检查是否获得了管理所有文件的权限
                if (Environment.isExternalStorageManager()) {
                    // 有权限，执行导出
                    exportToDownloads()
                } else {
                    Snackbar.make(binding.recycler, "需要存储权限才能导出文件", Snackbar.LENGTH_LONG).show()
                }
            }
        } else if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            // 处理SAF选择器的结果（保留作为后备）
            val uri = data?.data
            if (uri != null) {
                // ... 现有代码
            }
        }
    }

    // 执行导出操作
    private fun exportToDownloads() {
        val fileName = "bettercounter_export.csv"
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 首先检查是否有数据可以导出
                if (!viewModel.hasDataToExport()) {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.recycler, "没有计数器可导出", Snackbar.LENGTH_LONG).show()
                    }
                    return@launch
                }
                
                // 检查下载目录是否存在现有的导出文件
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                
                val file = java.io.File(downloadsDir, fileName)
                
                // 如果文件已存在且为空，先删除它
                if (file.exists() && file.length() == 0L) {
                    Log.d(TAG, "删除现有的空文件: ${file.absolutePath}")
                    file.delete()
                }
                
                Log.d(TAG, "开始导出到: ${file.absolutePath}")
                
                // 创建输出流
                val outputStream = java.io.FileOutputStream(file)
                val progressHandler = Handler(Looper.getMainLooper()) {
                    if (it.arg1 == it.arg2) {
                        // 验证文件已成功写入且非空
                        if (file.exists() && file.length() > 0) {
                            // 通知媒体扫描服务更新文件
                            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                            mediaScanIntent.data = Uri.fromFile(file)
                            sendBroadcast(mediaScanIntent)
                            
                            Snackbar.make(binding.recycler, "已导出到下载目录: $fileName", Snackbar.LENGTH_LONG).show()
                        } else {
                            Snackbar.make(binding.recycler, "导出失败: 文件为空", Snackbar.LENGTH_LONG).show()
                        }
                    }
                    true
                }
                
                // 传递true参数，让viewModel在完成后关闭流
                viewModel.exportAll(outputStream, progressHandler, true)
            } catch (e: Exception) {
                Log.e(TAG, "导出失败: ${e.javaClass.name}: ${e.message}")
                e.printStackTrace()
                
                // 确保在出错时也重置文件状态
                try {
                    val file = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                    if (file.exists() && file.length() == 0L) {
                        file.delete()
                    }
                } catch (cleanupEx: Exception) {
                    Log.e(TAG, "清理失败的导出文件时出错: ${cleanupEx.message}")
                }
                
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.recycler, "导出失败: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        entryViewAdapter.dismissTooltip()
    }
}
