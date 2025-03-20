package org.kde.bettercounter.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.R

class GroupSelectorActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var groupList: ListView
    private lateinit var applyButton: Button
    private var selectedGroupId: String = "default"
    private var selectedGroupPosition: Int = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_selector)
        
        // 设置结果为取消，以防用户在选择前返回
        setResult(RESULT_CANCELED)
        
        // 获取Widget ID
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, 
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        
        // 初始化视图
        groupList = findViewById(R.id.group_list)
        applyButton = findViewById(R.id.apply_button)
        
        // 读取当前选择的分组
        val currentGroupId = LargeWidgetConfigureActivity.loadGroupId(this, appWidgetId)
        
        // 设置分组列表
        setupGroupList(currentGroupId)
        
        // 设置确认按钮点击事件
        applyButton.setOnClickListener {
            android.util.Log.d("GroupSelectorActivity", "正在切换到分组: $selectedGroupId")
            
            // 保存选中的分组
            LargeWidgetConfigureActivity.savePref(
                appWidgetId, 
                LargeWidgetConfigureActivity.PREF_GROUP_ID_KEY, 
                selectedGroupId
            )
            
            // 更新Widget
            val appWidgetManager = AppWidgetManager.getInstance(this)
            LargeCounterWidget.updateAppWidget(this, appWidgetManager, appWidgetId)
            
            // 强制更新Widget列表数据
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
            
            // 设置结果
            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
    
    private fun setupGroupList(currentGroupId: String) {
        val viewModel = (application as BetterApplication).viewModel
        val groups = viewModel.getGroups()
        
        val groupNames = groups.map { it.name }.toTypedArray()
        val groupIds = groups.map { it.id }.toTypedArray()
        
        // 查找当前选择的分组位置
        val currentIndex = groupIds.indexOf(currentGroupId)
        if (currentIndex >= 0) {
            selectedGroupPosition = currentIndex
            selectedGroupId = currentGroupId
        }
        
        // 设置适配器
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, groupNames)
        groupList.adapter = adapter
        
        // 设置当前选中项
        groupList.choiceMode = ListView.CHOICE_MODE_SINGLE
        groupList.setItemChecked(selectedGroupPosition, true)
        
        // 监听选择变化
        groupList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            selectedGroupPosition = position
            selectedGroupId = groupIds[position]
        }
    }
} 