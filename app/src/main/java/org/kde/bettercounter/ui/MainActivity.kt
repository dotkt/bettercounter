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
import androidx.viewpager2.widget.ViewPager2
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
    private lateinit var categoryPagerAdapter: CategoryPagerAdapter
    private lateinit var binding: ActivityMainBinding
    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var searchResultAdapter: SearchResultAdapter
    private var intervalOverride: Interval? = null
    private var sheetIsExpanding = false
    private var currentSelectedCounter: CounterSummary? = null
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
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 不再使用 toolbar，改用自定义按钮

        viewModel = (application as BetterApplication).viewModel
        
        // 设置点击空白区域隐藏软键盘
        setupHideKeyboardOnClick()

        // Bottom sheet with graph
        // -----------------------
        sheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        sheetIsExpanding = false
        val sheetFoldedPadding = 100 // padding so the fab is in view
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
                    entryViewAdapter.clearItemSelected()
                    currentSelectedCounter = null
                    binding.editCounterButton.visibility = View.GONE
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (!sheetIsExpanding) { // only do this when collapsing. when expanding we set the final padding at once so smoothScrollToPosition can do its job
                    val bottomPadding =
                        sheetFoldedPadding + ((1.0 + slideOffset) * (sheetUnfoldedPadding - sheetFoldedPadding)).toInt()
                    // 更新当前页面的RecyclerView的padding
                    getCurrentRecyclerView()?.setPadding(0, 0, 0, bottomPadding)
                }
            }
        })

        onBackPressedDispatcher.addCallback(onBackPressedCloseSheetCallback)

        // Counter list with category pager
        // ------------
        categoryPagerAdapter = CategoryPagerAdapter(this, viewModel, object : EntryListViewAdapter.EntryListObserver {
            override fun onItemAdded(position: Int) {
                getCurrentRecyclerView()?.smoothScrollToPosition(position)
            }
            override fun onSelectedItemUpdated(position: Int, counter: CounterSummary) {
                binding.detailsTitle.text = counter.name
                currentSelectedCounter = counter
                // 设置编辑按钮的点击事件
                binding.editCounterButton.visibility = View.VISIBLE
                binding.editCounterButton.setOnClickListener {
                    showEditCounterDialog(counter)
                }
                val interval = intervalOverride ?: counter.interval.toChartDisplayableInterval()
                val adapter = ChartsAdapter(this@MainActivity, viewModel, counter, interval,
                    onIntervalChange = { newInterval ->
                        intervalOverride = newInterval
                        onSelectedItemUpdated(position, counter)
                    },
                    onDateChange = { newDate ->
                        val chartPosition = this.findPositionForRangeStart(newDate)
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
                getCurrentRecyclerView()?.let { recyclerView ->
                    recyclerView.setPadding(0, 0, 0, sheetUnfoldedPadding)
                    recyclerView.smoothScrollToPosition(position)
                }

                intervalOverride = null

                onSelectedItemUpdated(position, counter)
            }
        })
        
        binding.viewPager.adapter = categoryPagerAdapter
        
        // 标记适配器已初始化（但不立即更新分类，等待数据加载完成）
        categoryPagerAdapter.markInitialized()
        
        // 等待数据加载完成后再更新分类
        viewModel.observeCounterChange(object : ViewModel.CounterObserver {
            override fun onInitialCountersLoaded() {
                // 数据加载完成后，更新分类列表
                categoryPagerAdapter.updateCategories()
                viewModel.removeCounterChangeObserver(this)
            }
            override fun onCounterAdded(counterName: String) {}
            override fun onCounterRemoved(counterName: String) {}
            override fun onCounterRenamed(oldName: String, newName: String) {}
            override fun onCounterDecremented(counterName: String, oldEntryDate: Date) {}
        })
        
        // 设置 ViewPager2 和搜索结果 RecyclerView 的 paddingTop，为顶部区域留出空间
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                updateViewPagerPadding()
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
        
        // 持续监听布局变化，以便在多选工具栏显示/隐藏时更新padding
        binding.topBar.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                updateViewPagerPadding()
            }
        })
        
        // 如果有分类，预先创建第一个分类的适配器
        if (categoryPagerAdapter.itemCount > 0) {
            val firstCategory = categoryPagerAdapter.getCategoryAt(0)
            categoryPagerAdapter.ensureAdapterForCategory(firstCategory)
            entryViewAdapter = categoryPagerAdapter.getAdapterForCategory(firstCategory)!!
            // 更新分类导航栏
            updateCategoryNavigation(firstCategory)
        } else {
            // 如果没有分类，创建一个默认的适配器（显示所有计数器）
            entryViewAdapter = EntryListViewAdapter(this, viewModel, object : EntryListViewAdapter.EntryListObserver {
                override fun onItemAdded(position: Int) {
                    getCurrentRecyclerView()?.smoothScrollToPosition(position)
                }
                override fun onSelectedItemUpdated(position: Int, counter: CounterSummary) {
                    binding.detailsTitle.text = counter.name
                    currentSelectedCounter = counter
                    // 设置编辑按钮的点击事件
                    binding.editCounterButton.visibility = View.VISIBLE
                    binding.editCounterButton.setOnClickListener {
                        showEditCounterDialog(counter)
                    }
                    val interval = intervalOverride ?: counter.interval.toChartDisplayableInterval()
                    val adapter = ChartsAdapter(this@MainActivity, viewModel, counter, interval,
                        onIntervalChange = { newInterval ->
                            intervalOverride = newInterval
                            onSelectedItemUpdated(position, counter)
                        },
                        onDateChange = { newDate ->
                            val chartPosition = this.findPositionForRangeStart(newDate)
                            binding.charts.scrollToPosition(chartPosition)
                        },
                        onDataDisplayed = {
                            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                        }
                    )
                    binding.charts.swapAdapter(adapter, true)
                    binding.charts.scrollToPosition(adapter.itemCount - 1)
                }
                override fun onItemSelected(position: Int, counter: CounterSummary) {
                    if (sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                        onBackPressedCloseSheetCallback.isEnabled = true
                        sheetIsExpanding = true
                    }
                    getCurrentRecyclerView()?.let { recyclerView ->
                        recyclerView.setPadding(0, 0, 0, sheetUnfoldedPadding)
                        recyclerView.smoothScrollToPosition(position)
                    }
                    intervalOverride = null
                    onSelectedItemUpdated(position, counter)
                }
            })
            // 没有分类时不显示导航栏
            binding.categoryContainer.removeAllViews()
        }
        
        // 添加页面切换监听器，更新 toolbar 标题并输出日志
        var previousPosition = binding.viewPager.currentItem
        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (categoryPagerAdapter.itemCount > 0) {
                    val oldCategory = if (previousPosition == position) {
                        "初始加载"
                    } else if (previousPosition < categoryPagerAdapter.itemCount) {
                        categoryPagerAdapter.getCategoryAt(previousPosition)
                    } else {
                        "未知"
                    }
                    val newCategory = categoryPagerAdapter.getCategoryAt(position)
                    Log.d(TAG, "========== 分类切换 ==========")
                    Log.d(TAG, "旧位置: $previousPosition -> 新位置: $position")
                    Log.d(TAG, "旧分类: $oldCategory")
                    Log.d(TAG, "新分类: $newCategory")
                    Log.d(TAG, "总页面数: ${categoryPagerAdapter.itemCount}")
                    Log.d(TAG, "所有分类列表: ${categoryPagerAdapter.getAllCategoriesForLog()}")
                    Log.d(TAG, "ViewModel中的分类: ${viewModel.getAllCategories()}")
                    Log.d(TAG, "============================")
                    
                    // 更新分类导航栏
                    updateCategoryNavigation(newCategory)
                    previousPosition = position
                }
            }
        })

        binding.charts.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false).apply {
            stackFromEnd = true
        }

        binding.charts.isNestedScrollingEnabled = false
        PagerSnapHelper().attachToRecyclerView(binding.charts) // Scroll one by one

        // 搜索功能
        // ---------
        // 初始化搜索结果 RecyclerView
        binding.searchResultsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        searchResultAdapter = SearchResultAdapter(viewModel) { counterName, category ->
            // 点击搜索结果时，跳转到对应分类并定位到计数器
            navigateToCounter(counterName, category)
        }
        binding.searchResultsRecyclerView.adapter = searchResultAdapter
        
        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val searchText = s?.toString()?.trim() ?: ""
                if (searchText.isEmpty()) {
                    // 清空搜索，显示分类列表
                    showCategoryList()
                } else {
                    // 有搜索文本，显示搜索结果
                    showSearchResults(searchText)
                }
            }
        })

        // 清除搜索按钮点击事件
        binding.searchLayout.setEndIconOnClickListener {
            binding.searchEditText.text?.clear()
            showCategoryList()
        }

        // 导出按钮点击事件
        binding.exportButton.setOnClickListener {
            checkPermissionAndExport()
        }

        // 设置菜单按钮
        setupMenuButton()
        
        // 设置多选功能
        setupMultiSelect()
        
        startRefreshEveryMinuteBoundary()
        
        // Handle intent after all UI components are initialized
        handleIntent(intent)
        
        // 延迟执行 widget 刷新，避免阻塞 UI 初始化
        lifecycleScope.launch(Dispatchers.Main) {
            delay(100) // 给 UI 更多时间初始化
            
            // For some reason, when the app is installed via Android Studio, the broadcast that
            // refreshes the widgets after installing doesn't trigger. Do it manually here.
            forceRefreshWidgets(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        Log.d(TAG, "========== handleIntent 开始 ==========")
        Log.d(TAG, "Intent: $intent")
        intent?.extras?.let { bundle ->
            for (key in bundle.keySet()) {
                Log.d(TAG, "Extra: $key = ${bundle.get(key)}")
            }
        }
        
        intent?.getStringExtra(EXTRA_COUNTER_NAME)?.let { counterName ->
            Log.d(TAG, "========== Widget点击: 计数器名称 = $counterName ==========")
            // 等待数据加载完成后再滚动
            viewModel.observeCounterChange(object : ViewModel.CounterObserver {
                override fun onInitialCountersLoaded() {
                    Log.d(TAG, "数据加载完成，开始定位计数器: $counterName")
                    // 找到计数器所属的分类
                    val category = viewModel.getCounterCategory(counterName)
                    Log.d(TAG, "计数器分类: $category")
                    
                    if (categoryPagerAdapter.itemCount > 0) {
                        val categoryPosition = categoryPagerAdapter.findPositionForCategory(category)
                        Log.d(TAG, "分类位置: $categoryPosition, 总分类数: ${categoryPagerAdapter.itemCount}")
                        
                        // 切换到对应的分类页面
                        binding.viewPager.setCurrentItem(categoryPosition, false)
                        Log.d(TAG, "已切换到分类页面: $categoryPosition")
                        
                        // 更新分类导航栏
                        updateCategoryNavigation(category)
                        
                        // 等待页面切换完成后再滚动
                        binding.viewPager.post {
                            // 等待ViewPager页面切换动画完成
                            binding.viewPager.postDelayed({
                                val adapter = categoryPagerAdapter.getAdapterForCategory(category)
                                Log.d(TAG, "获取适配器: ${if (adapter != null) "成功" else "失败"}")
                                
                                val position = adapter?.let { adapter ->
                                    val positionInAdapter = adapter.getItemPosition(counterName)
                                    Log.d(TAG, "在适配器中的位置: $positionInAdapter")
                                    positionInAdapter
                                } ?: -1
                                
                                Log.d(TAG, "最终位置: $position")
                                
                                if (position != -1) {
                                    getCurrentRecyclerView()?.let { rv ->
                                        Log.d(TAG, "找到RecyclerView，准备滚动")
                                        Log.d(TAG, "RecyclerView状态 - isLaidOut: ${rv.isLaidOut}, childCount: ${rv.childCount}, adapter: ${rv.adapter != null}")
                                        rv.post {
                                            scrollToPositionTopAfterLayout(rv, position, counterName)
                                        }
                                    } ?: Log.e(TAG, "错误: 无法获取RecyclerView")
                                } else {
                                    Log.e(TAG, "错误: 找不到计数器位置，position = -1")
                                }
                            }, 100) // 延迟100ms等待ViewPager切换完成
                        }
                    } else {
                        Log.d(TAG, "没有分类页面，直接滚动")
                        // 如果没有分类页面，直接滚动
                        getCurrentRecyclerView()?.let { rv ->
                            val position = viewModel.getCounterList().indexOf(counterName)
                            Log.d(TAG, "直接滚动位置: $position")
                            if (position != -1) {
                                rv.post {
                                    scrollToPositionTopAfterLayout(rv, position, counterName)
                                }
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

    /**
     * 显示分类列表（正常模式）
     */
    private fun showCategoryList() {
        binding.viewPager.visibility = View.VISIBLE
        binding.searchResultsRecyclerView.visibility = View.GONE
        // 更新分类导航栏
        if (categoryPagerAdapter.itemCount > 0) {
            val currentPosition = binding.viewPager.currentItem
            if (currentPosition < categoryPagerAdapter.itemCount) {
                val category = categoryPagerAdapter.getCategoryAt(currentPosition)
                updateCategoryNavigation(category)
            }
        }
    }
    
    /**
     * 显示搜索结果
     */
    private fun showSearchResults(searchText: String) {
        binding.viewPager.visibility = View.GONE
        binding.searchResultsRecyclerView.visibility = View.VISIBLE
        updateToolbarTitle("搜索结果")
        
        // 搜索所有分类的计数器
        val allCounters = viewModel.getCounterList()
        val resultsByCategory = mutableMapOf<String, MutableList<String>>()
        
        allCounters.forEach { counterName ->
            if (counterName.contains(searchText, ignoreCase = true)) {
                val category = viewModel.getCounterCategory(counterName)
                if (!resultsByCategory.containsKey(category)) {
                    resultsByCategory[category] = mutableListOf()
                }
                resultsByCategory[category]!!.add(counterName)
            }
        }
        
        // 更新搜索结果适配器
        searchResultAdapter.updateResults(resultsByCategory)
    }
    
    /**
     * 跳转到指定分类的指定计数器（仅定位，不打开配置）
     */
    private fun navigateToCounter(counterName: String, category: String) {
        // 切换到对应分类页面
        val categoryPosition = categoryPagerAdapter.findPositionForCategory(category)
        if (categoryPosition != -1) {
            binding.viewPager.setCurrentItem(categoryPosition, false)
            
            // 等待页面切换完成后再滚动到计数器
            binding.viewPager.post {
                val adapter = categoryPagerAdapter.getAdapterForCategory(category)
                adapter?.let {
                    // 找到计数器在适配器中的位置
                    val position = it.getItemPosition(counterName)
                    Log.d(TAG, "搜索结果点击 - 计数器: $counterName, 位置: $position")
                    if (position != -1) {
                        getCurrentRecyclerView()?.post {
                            scrollToPositionTopAfterLayout(getCurrentRecyclerView(), position, counterName)
                        }
                    }
                }
            }
        }
        
        // 清空搜索框
        binding.searchEditText.text?.clear()
        showCategoryList()
    }
    
    private fun getCurrentAdapter(): EntryListViewAdapter? {
        if (categoryPagerAdapter.itemCount == 0) {
            return entryViewAdapter
        }
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition < categoryPagerAdapter.itemCount) {
            val category = categoryPagerAdapter.getCategoryAt(currentPosition)
            return categoryPagerAdapter.getAdapterForCategory(category)
        }
        return null
    }
    
    private fun updateToolbarTitle(categoryName: String) {
        updateCategoryNavigation(categoryName)
    }
    
    /**
     * 当分类列表更新时调用
     */
    fun onCategoriesUpdated() {
        if (categoryPagerAdapter.itemCount > 0) {
            val currentPosition = binding.viewPager.currentItem
            if (currentPosition < categoryPagerAdapter.itemCount) {
                val category = categoryPagerAdapter.getCategoryAt(currentPosition)
                updateCategoryNavigation(category)
            }
        }
    }
    
    /**
     * 更新分类导航栏
     */
    private fun updateCategoryNavigation(currentCategory: String) {
        // 清除现有视图
        binding.categoryContainer.removeAllViews()
        
        // 获取所有分类
        val allCategories = categoryPagerAdapter.getAllCategoriesForLog()
        
        // 为每个分类创建按钮
        allCategories.forEach { category ->
            val button = if (category == currentCategory) {
                // 当前分类：使用填充按钮样式，高亮显示
                com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle)
            } else {
                // 其他分类：使用轮廓按钮样式
                com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            }.apply {
                text = category
                textSize = 14f
                minWidth = 0
                minHeight = 0
                setPadding(16, 8, 16, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 8
                }
                
                // 根据是否为当前分类设置样式
                if (category == currentCategory) {
                    // 当前分类：高亮显示
                    setBackgroundColor(getColor(R.color.colorAccent))
                    setTextColor(getColor(android.R.color.white))
                    elevation = 4f
                } else {
                    // 其他分类：轮廓样式
                    setTextColor(getColor(R.color.colorAccent))
                    elevation = 0f
                }
                
                // 点击跳转到对应分类
                setOnClickListener {
                    val position = categoryPagerAdapter.findPositionForCategory(category)
                    if (position != -1) {
                        binding.viewPager.setCurrentItem(position, true)
                    }
                }
            }
            
            binding.categoryContainer.addView(button)
        }
        
        // 滚动到当前分类按钮
        binding.categoryScrollView.post {
            // 找到当前分类按钮并滚动到它
            for (i in 0 until binding.categoryContainer.childCount) {
                val view = binding.categoryContainer.getChildAt(i)
                if (view is com.google.android.material.button.MaterialButton && view.text == currentCategory) {
                    val scrollX = view.left - (binding.categoryScrollView.width - view.width) / 2
                    binding.categoryScrollView.smoothScrollTo(
                        scrollX.coerceAtLeast(0),
                        0
                    )
                    break
                }
            }
        }
    }
    
    /**
     * 设置点击空白区域隐藏软键盘
     */
    private fun setupHideKeyboardOnClick() {
        // 这个方法现在不需要做任何事情，因为我们在dispatchTouchEvent中处理
    }
    
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
            // 使用HitTest来查找点击位置的View
            val x = ev.x.toInt()
            val y = ev.y.toInt()
            val view = findViewAtScreenCoordinates(ev.rawX.toInt(), ev.rawY.toInt())
            
            // 检查点击的View是否是按钮
            if (view != null && isButton(view)) {
                // 点击的是按钮，隐藏键盘
                hideKeyboard()
            } else {
                // 检查当前是否有EditText获得焦点
                val currentFocus = currentFocus
                if (currentFocus is android.widget.EditText) {
                    // 检查点击位置是否在EditText内
                    val location = IntArray(2)
                    currentFocus.getLocationOnScreen(location)
                    val rawX = ev.rawX.toInt()
                    val rawY = ev.rawY.toInt()
                    val left = location[0]
                    val top = location[1]
                    val right = left + currentFocus.width
                    val bottom = top + currentFocus.height
                    
                    // 如果点击位置不在EditText内，隐藏键盘
                    if (rawX < left || rawX > right || rawY < top || rawY > bottom) {
                        hideKeyboard()
                    }
                } else {
                    // 没有EditText获得焦点，也尝试隐藏键盘（处理残留状态）
                    hideKeyboard()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
    
    /**
     * 在屏幕坐标查找View
     */
    private fun findViewAtScreenCoordinates(screenX: Int, screenY: Int): android.view.View? {
        val location = IntArray(2)
        binding.root.getLocationOnScreen(location)
        val x = screenX - location[0]
        val y = screenY - location[1]
        return findViewAt(binding.root, x, y)
    }
    
    /**
     * 在指定坐标查找View
     */
    private fun findViewAt(parent: android.view.View, x: Int, y: Int): android.view.View? {
        if (x < 0 || y < 0 || x >= parent.width || y >= parent.height) {
            return null
        }
        
        if (parent is android.view.ViewGroup) {
            for (i in parent.childCount - 1 downTo 0) {
                val child = parent.getChildAt(i)
                if (child.visibility == android.view.View.VISIBLE) {
                    val childX = x - child.left
                    val childY = y - child.top
                    if (childX >= 0 && childY >= 0 && childX < child.width && childY < child.height) {
                        val found = findViewAt(child, childX, childY)
                        if (found != null) {
                            return found
                        }
                    }
                }
            }
        }
        
        return parent
    }
    
    /**
     * 检查View是否是按钮
     */
    private fun isButton(view: android.view.View): Boolean {
        return view is android.widget.Button ||
               view is com.google.android.material.button.MaterialButton ||
               view is androidx.appcompat.widget.AppCompatButton ||
               view.javaClass.simpleName.contains("Button", ignoreCase = true)
    }
    
    /**
     * 隐藏软键盘并清除输入框焦点
     */
    private fun hideKeyboard() {
        // 获取当前获得焦点的View
        val currentFocus = currentFocus
        if (currentFocus is android.widget.EditText) {
            // 清除焦点
            currentFocus.clearFocus()
            // 隐藏软键盘
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        } else {
            // 即使没有焦点在EditText上，也尝试隐藏键盘（处理残留状态）
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val view = window.currentFocus ?: binding.root
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun getCurrentRecyclerView(): RecyclerView? {
        // ViewPager2 内部使用 RecyclerView，我们需要获取当前页面的 RecyclerView
        // ViewPager2 的第一个子视图就是内部的 RecyclerView
        val viewPagerRecyclerView = binding.viewPager.getChildAt(0) as? RecyclerView ?: return null
        
        // 获取当前显示的页面位置
        val currentItem = binding.viewPager.currentItem
        
        // 查找当前页面的 ViewHolder
        val viewHolder = viewPagerRecyclerView.findViewHolderForAdapterPosition(currentItem)
        if (viewHolder is CategoryPagerAdapter.CategoryViewHolder) {
            return viewHolder.recyclerView
        }
        
        // 如果找不到 ViewHolder，尝试通过其他方式获取
        // ViewPager2 的每个页面都是一个 RecyclerView
        val pageView = viewPagerRecyclerView.findViewHolderForAdapterPosition(currentItem)?.itemView
        if (pageView is RecyclerView) {
            return pageView
        }
        
        return null
    }
    
    /**
     * 将 RecyclerView 中的指定位置滚动到顶部
     * 使用 scrollToPositionWithOffset 确保项目精确地处于置顶位置
     */
    private fun scrollToPositionTop(recyclerView: RecyclerView?, position: Int, counterName: String = "") {
        recyclerView?.let { rv ->
            val layoutManager = rv.layoutManager
            Log.d(TAG, "========== scrollToPositionTop 开始 ==========")
            Log.d(TAG, "计数器: $counterName, 位置: $position")
            Log.d(TAG, "LayoutManager类型: ${layoutManager?.javaClass?.simpleName}")
            Log.d(TAG, "RecyclerView状态 - isLaidOut: ${rv.isLaidOut}, width: ${rv.width}, height: ${rv.height}")
            Log.d(TAG, "RecyclerView子视图数量: ${rv.childCount}")
            
            if (layoutManager is androidx.recyclerview.widget.LinearLayoutManager) {
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                Log.d(TAG, "当前可见范围: $firstVisible 到 $lastVisible")
                
                // 使用 scrollToPositionWithOffset 精确地将项目滚动到顶部
                // 第二个参数 0 表示偏移量为0，即项目顶部对齐 RecyclerView 顶部
                layoutManager.scrollToPositionWithOffset(position, 0)
                Log.d(TAG, "已调用 scrollToPositionWithOffset($position, 0)")
                
                // 延迟检查滚动结果
                rv.postDelayed({
                    val newFirstVisible = layoutManager.findFirstVisibleItemPosition()
                    val newLastVisible = layoutManager.findLastVisibleItemPosition()
                    Log.d(TAG, "滚动后可见范围: $newFirstVisible 到 $newLastVisible")
                    Log.d(TAG, "目标位置 $position 是否在可见范围: ${position in newFirstVisible..newLastVisible}")
                    if (newFirstVisible == position) {
                        Log.d(TAG, "✓ 成功: 位置 $position 已置顶")
                    } else {
                        Log.w(TAG, "✗ 警告: 位置 $position 未置顶，当前第一个可见项: $newFirstVisible")
                    }
                    Log.d(TAG, "========== scrollToPositionTop 结束 ==========")
                }, 100)
            } else {
                Log.w(TAG, "LayoutManager不是LinearLayoutManager，使用默认方法")
                // 如果不是 LinearLayoutManager，使用默认方法
                rv.scrollToPosition(position)
            }
        } ?: Log.e(TAG, "错误: RecyclerView为null")
    }
    
    /**
     * 等待 RecyclerView 布局完成后，将指定位置滚动到顶部
     */
    private fun scrollToPositionTopAfterLayout(recyclerView: RecyclerView?, position: Int, counterName: String = "") {
        recyclerView?.let { rv ->
            Log.d(TAG, "========== scrollToPositionTopAfterLayout 开始 ==========")
            Log.d(TAG, "计数器: $counterName, 位置: $position")
            Log.d(TAG, "RecyclerView isLaidOut: ${rv.isLaidOut}")
            
            // 检查布局是否已经完成
            if (rv.isLaidOut) {
                Log.d(TAG, "布局已完成，直接滚动")
                // 布局已完成，直接滚动
                scrollToPositionTop(rv, position, counterName)
            } else {
                Log.d(TAG, "布局未完成，等待布局完成")
                // 布局未完成，等待布局完成后再滚动
                rv.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        rv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        Log.d(TAG, "布局完成，开始滚动")
                        scrollToPositionTop(rv, position, counterName)
                    }
                })
            }
            Log.d(TAG, "========== scrollToPositionTopAfterLayout 结束 ==========")
        } ?: Log.e(TAG, "错误: RecyclerView为null")
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
        // 不再使用 OptionsMenu，改用自定义按钮
        return false
    }
    
    private fun setupMenuButton() {
        binding.menuButton.setOnClickListener {
            // 创建弹出菜单
            val popupMenu = android.widget.PopupMenu(this, binding.menuButton)
            popupMenu.menuInflater.inflate(R.menu.main, popupMenu.menu)
            // 添加测试菜单项
            popupMenu.menu.add(Menu.NONE, 999, Menu.NONE, "测试日期变化")
            // 添加多选菜单项
            popupMenu.menu.add(Menu.NONE, 1000, Menu.NONE, "多选模式")
            
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1000 -> {
                        enterMultiSelectMode()
                        true
                    }
                    else -> onOptionsItemSelected(item)
                }
            }
            popupMenu.show()
        }
    }
    
    /**
     * 设置多选功能
     */
    private fun setupMultiSelect() {
        // 取消多选按钮
        binding.cancelMultiSelectButton.setOnClickListener {
            exitMultiSelectMode()
        }
        
        // 批量修改分类按钮
        binding.batchChangeCategoryButton.setOnClickListener {
            showBatchChangeCategoryDialog()
        }
    }
    
    /**
     * 更新ViewPager的padding，确保内容不被顶部工具栏遮挡
     */
    private fun updateViewPagerPadding() {
        val topBarHeight = binding.topBar.height
        binding.viewPager.setPadding(0, topBarHeight, 0, 0)
        binding.searchResultsRecyclerView.setPadding(0, topBarHeight, 0, 0)
    }
    
    /**
     * 进入多选模式
     */
    private fun enterMultiSelectMode() {
        // 更新所有分类页面的适配器
        categoryPagerAdapter.setMultiSelectModeForAll(true)
        // 显示多选工具栏
        binding.multiSelectToolbar.visibility = android.view.View.VISIBLE
        // 更新选中数量
        updateSelectedCount()
        // 延迟更新padding，等待工具栏布局完成
        binding.multiSelectToolbar.post {
            updateViewPagerPadding()
        }
    }
    
    /**
     * 退出多选模式
     */
    private fun exitMultiSelectMode() {
        // 更新所有分类页面的适配器
        categoryPagerAdapter.setMultiSelectModeForAll(false)
        // 隐藏多选工具栏
        binding.multiSelectToolbar.visibility = android.view.View.GONE
        // 更新padding
        binding.topBar.post {
            updateViewPagerPadding()
        }
    }
    
    /**
     * 更新选中数量显示
     */
    fun updateSelectedCount() {
        val selectedCount = categoryPagerAdapter.getTotalSelectedCount()
        binding.selectedCountText.text = "已选择 $selectedCount 项"
        binding.batchChangeCategoryButton.isEnabled = selectedCount > 0
    }
    
    /**
     * 显示批量修改分类对话框
     */
    private fun showBatchChangeCategoryDialog() {
        val selectedCounters = categoryPagerAdapter.getAllSelectedCounters()
        if (selectedCounters.isEmpty()) {
            Snackbar.make(binding.root, "请先选择要修改的计数器", Snackbar.LENGTH_SHORT).show()
            return
        }
        
        // 获取所有分类
        val allCategories = viewModel.getAllCategories().sorted().toMutableList()
        if (!allCategories.contains("默认")) {
            allCategories.add(0, "默认")
        }
        
        // 创建分类选择对话框
        MaterialAlertDialogBuilder(this)
            .setTitle("选择新分类")
            .setItems(allCategories.toTypedArray()) { _, which ->
                val newCategory = allCategories[which]
                batchChangeCategory(selectedCounters, newCategory)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 批量修改分类
     */
    private fun batchChangeCategory(counterNames: Set<String>, newCategory: String) {
        var successCount = 0
        var failCount = 0
        
        counterNames.forEach { counterName ->
            try {
                val counter = viewModel.getCounterSummary(counterName).value
                if (counter != null) {
                    val meta = org.kde.bettercounter.persistence.CounterMetadata(
                        counter.name, counter.interval, counter.goal, counter.color, newCategory
                    )
                    if (counter.name != meta.name) {
                        viewModel.editCounter(counter.name, meta)
                    } else {
                        viewModel.editCounterSameName(meta)
                    }
                    successCount++
                } else {
                    failCount++
                }
            } catch (e: Exception) {
                failCount++
            }
        }
        
        // 退出多选模式
        exitMultiSelectMode()
        
        // 更新分类列表
        categoryPagerAdapter.updateCategories()
        
        // 显示结果
        val message = if (failCount > 0) {
            "成功修改 $successCount 个，失败 $failCount 个"
        } else {
            "成功修改 $successCount 个计数器的分类"
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    private fun showAddCounterDialog() {
        CounterSettingsDialogBuilder(this@MainActivity, viewModel)
            .forNewCounter()
            .setOnSaveListener { counterMetadata ->
                viewModel.addCounter(counterMetadata)
                // 更新分类列表
                categoryPagerAdapter.updateCategories()
            }
            .setOnDismissListener { }
            .show()
    }

    fun showChangeGraphIntervalTutorial(onDismissListener: SimpleTooltip.OnDismissListener? = null) {
        val adapter = binding.charts.adapter!!
        binding.charts.scrollToPosition(adapter.itemCount - 1)
        val holder = binding.charts.findViewHolderForAdapterPosition(adapter.itemCount - 1) as ChartHolder
        holder.showChangeGraphIntervalTutorial(onDismissListener)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.add_counter -> {
                showAddCounterDialog()
                return true
            }
            R.id.export_csv -> {
                // 检查并请求权限，然后导出
                checkPermissionAndExport()
            }
            R.id.import_csv -> {
                importFilePicker.launch(OpenFileParams("text/*"))
            }
            R.id.clear_all_data -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.clear_all_data_confirm_title)
                    .setMessage(R.string.clear_all_data_confirm_message)
                    .setPositiveButton(R.string.clear) { _, _ ->
                        viewModel.clearAllData()
                        Snackbar.make(binding.viewPager, R.string.all_data_cleared, Snackbar.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            R.id.show_tutorial -> {
                if (viewModel.getCounterList().isEmpty()) {
                    Snackbar.make(binding.viewPager, getString(R.string.no_counters), Snackbar.LENGTH_LONG).show()
                } else {
                    getCurrentRecyclerView()?.scrollToPosition(0)
                    val holder = getCurrentRecyclerView()?.findViewHolderForAdapterPosition(0) as? EntryViewHolder
                    if (holder == null) return@onOptionsItemSelected true
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
                        Snackbar.make(binding.viewPager, "已触发计数器刷新", Snackbar.LENGTH_SHORT).show()
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

    private fun showEditCounterDialog(counter: CounterSummary) {
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
            .setOnDismissListener { }
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
                        Snackbar.make(binding.viewPager, "无法请求权限，请在系统设置中手动授予", Snackbar.LENGTH_LONG).show()
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
                    Snackbar.make(binding.viewPager, "需要存储权限才能导出文件", Snackbar.LENGTH_LONG).show()
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
                    Snackbar.make(binding.viewPager, "需要存储权限才能导出文件", Snackbar.LENGTH_LONG).show()
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
                        Snackbar.make(binding.viewPager, "没有计数器可导出", Snackbar.LENGTH_LONG).show()
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
                            
                            Snackbar.make(binding.viewPager, "已导出到下载目录: $fileName", Snackbar.LENGTH_LONG).show()
                        } else {
                            Snackbar.make(binding.viewPager, "导出失败: 文件为空", Snackbar.LENGTH_LONG).show()
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
                    Snackbar.make(binding.viewPager, "导出失败: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        entryViewAdapter.dismissTooltip()
    }
}
