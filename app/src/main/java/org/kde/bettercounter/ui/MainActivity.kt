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

        startRefreshEveryHourBoundary()
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
        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(millisecondsUntilNextHour())
                viewModel.refreshAllObservers()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main, menu)
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
                // 使用 MediaStore API 创建文件
                val fileName = "bettercounter_export.csv"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                try {
                    val uri = contentResolver.insert(MediaStore.Downloads.getContentUri("external"), values)
                    uri?.let { outputUri ->
                        contentResolver.openOutputStream(outputUri)?.let { stream ->
                            val progressDialogBinding = ProgressDialogBinding.inflate(layoutInflater)
                            val dialog = MaterialAlertDialogBuilder(this)
                                .setView(progressDialogBinding.root)
                                .setCancelable(false)
                                .create()
                            dialog.show()

                            val progressHandler = Handler(Looper.getMainLooper()) {
                                progressDialogBinding.text.text =
                                    getString(R.string.exported_n, it.arg1, it.arg2)
                                if (it.arg1 == it.arg2) {
                                    dialog.setCancelable(true)
                                    Snackbar.make(binding.recycler, "已导出到下载目录: $fileName", Snackbar.LENGTH_LONG).show()
                                }
                                true
                            }
                            viewModel.exportAll(stream, progressHandler)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to export: ${e.message}")
                    Snackbar.make(binding.recycler, "导出失败: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
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
}
