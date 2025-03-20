package org.kde.bettercounter.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.R
import org.kde.bettercounter.ViewModel

/**
 * 大型小部件的配置活动，允许用户选择分组
 */
class LargeWidgetConfigureActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var addWidgetButton: Button
    private lateinit var groupSpinner: Spinner
    private var selectedGroupId: String = "default"
    private lateinit var viewModel: ViewModel

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置结果为取消，以防用户在配置前点击了后退
        setResult(RESULT_CANCELED)
        
        // 设置布局
        setContentView(R.layout.large_widget_configure)
        
        // 初始化ViewModel
        viewModel = (application as BetterApplication).viewModel
        
        // 初始化视图
        addWidgetButton = findViewById(R.id.add_button)
        groupSpinner = findViewById(R.id.group_spinner)
        
        // 设置分组选择器
        setupGroupSpinner()
        
        // 从Intent获取Widget ID
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }
        
        // 如果没有获取有效的Widget ID，则关闭活动
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        
        // 设置添加按钮点击事件
        addWidgetButton.setOnClickListener {
            val context = this@LargeWidgetConfigureActivity
            
            // 保存选择的分组ID
            savePref(appWidgetId, PREF_GROUP_ID_KEY, selectedGroupId)
            
            // 更新Widget
            val appWidgetManager = AppWidgetManager.getInstance(context)
            LargeCounterWidget.updateAppWidget(context, appWidgetManager, appWidgetId)
            
            // 创建结果Intent
            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }

    private fun setupGroupSpinner() {
        val groups = viewModel.getGroups()
        
        // 创建分组名称列表用于显示
        val groupNames = groups.map { it.name }.toTypedArray()
        val groupIds = groups.map { it.id }.toTypedArray()
        
        // 设置适配器
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, groupNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        groupSpinner.adapter = adapter
        
        // 设置默认选中的分组
        val savedGroupId = loadPref(appWidgetId, PREF_GROUP_ID_KEY, "default")
        val groupIndex = groupIds.indexOf(savedGroupId)
        if (groupIndex >= 0) {
            groupSpinner.setSelection(groupIndex)
        }
        
        // 监听选择变化
        groupSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedGroupId = groupIds[position]
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedGroupId = "default"
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "org.kde.bettercounter.widget.LargeCounterWidget"
        private const val PREF_PREFIX_KEY = "large_widget_"
        const val PREF_GROUP_ID_KEY = "widget_group_id_"
        
        // 保存偏好设置
        internal fun savePref(appWidgetId: Int, key: String, value: String) {
            val context = BetterApplication.getInstance()
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            prefs.putString(PREF_PREFIX_KEY + key + appWidgetId, value)
            prefs.apply()
        }
        
        // 加载偏好设置
        internal fun loadPref(appWidgetId: Int, key: String, defaultValue: String): String {
            val context = BetterApplication.getInstance()
            val prefs = context.getSharedPreferences(PREFS_NAME, 0)
            return prefs.getString(PREF_PREFIX_KEY + key + appWidgetId, defaultValue) ?: defaultValue
        }
        
        // 删除偏好设置
        internal fun deletePref(appWidgetId: Int) {
            val context = BetterApplication.getInstance()
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            prefs.remove(PREF_PREFIX_KEY + appWidgetId)
            prefs.apply()
        }
        
        // 加载分组ID
        fun loadGroupId(context: Context, appWidgetId: Int): String {
            return loadPref(appWidgetId, PREF_GROUP_ID_KEY, "default")
        }
    }
} 