package org.kde.bettercounter.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import org.kde.bettercounter.R
import org.kde.bettercounter.databinding.ListWidgetConfigureBinding

class ListWidgetConfigureActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var binding: ListWidgetConfigureBinding

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // 设置结果为取消，以防用户直接按返回键
        setResult(RESULT_CANCELED)

        binding = ListWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 找到 widget ID
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // 如果没有有效的 widget ID，就退出
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        binding.addButton.setOnClickListener {
            val context = this@ListWidgetConfigureActivity

            // 更新 widget
            val appWidgetManager = AppWidgetManager.getInstance(context)
            ListWidgetProvider.updateListWidget(context, appWidgetManager, appWidgetId)

            // 返回成功结果
            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
} 