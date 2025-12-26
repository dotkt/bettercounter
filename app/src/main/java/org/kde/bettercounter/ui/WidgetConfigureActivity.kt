package org.kde.bettercounter.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.R
import org.kde.bettercounter.ViewModel
import org.kde.bettercounter.databinding.WidgetConfigureBinding
import android.graphics.drawable.GradientDrawable

class WidgetConfigureActivity : AppCompatActivity() {

    private lateinit var viewModel: ViewModel
    private lateinit var binding: WidgetConfigureBinding
    private lateinit var adapter: CounterListAdapter

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var currentCategory: String? = null
    private var allCounters: List<String> = emptyList()
    private var filteredCounters: MutableList<String> = mutableListOf()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = WidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Cancel widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        viewModel = (application as BetterApplication).viewModel
        allCounters = viewModel.getCounterList()
        if (allCounters.isEmpty()) {
            Toast.makeText(this, R.string.no_counters, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 设置 RecyclerView
        binding.counterNamesList.layoutManager = LinearLayoutManager(this)
        adapter = CounterListAdapter(allCounters) { counterName ->
            saveWidgetCounterNamePref(this, appWidgetId, counterName)
            val appWidgetManager = AppWidgetManager.getInstance(this)
            updateAppWidget(this, viewModel, appWidgetManager, appWidgetId)
            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
        binding.counterNamesList.adapter = adapter

        // 设置搜索功能
        setupSearch()

        // 设置分类导航
        setupCategoryNavigation()

        // 初始显示所有计数器
        applyFilters()
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilters()
            }
        })
    }

    private fun setupCategoryNavigation() {
        val allCategories = viewModel.getAllCategories().sorted()
        
        // 只使用现有的分类，不添加"全部"选项
        val categories = allCategories.toMutableList()

        binding.categoryContainer.removeAllViews()

        categories.forEach { category ->
            val textView = TextView(this).apply {
                text = category
                textSize = 14f
                setPadding(8, 6, 8, 6)

                val drawable = GradientDrawable().apply {
                    cornerRadius = 8f
                    if (category == currentCategory) {
                        setColor(getColor(R.color.colorAccent))
                        setStroke(0, android.graphics.Color.TRANSPARENT)
                        setTextColor(getColor(android.R.color.white))
                    } else {
                        setColor(android.graphics.Color.TRANSPARENT)
                        setStroke(2, getColor(R.color.colorAccent))
                        setTextColor(getColor(R.color.colorAccent))
                    }
                }
                background = drawable

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 8
                }

                setOnClickListener {
                    currentCategory = category
                    setupCategoryNavigation() // 重新设置以更新高亮
                    applyFilters()
                }
            }

            binding.categoryContainer.addView(textView)
        }
    }

    private fun applyFilters() {
        filteredCounters.clear()
        filteredCounters.addAll(allCounters)

        // 按分类过滤
        if (currentCategory != null) {
            filteredCounters = filteredCounters.filter { counterName ->
                viewModel.getCounterCategory(counterName) == currentCategory
            }.toMutableList()
        }

        // 按搜索文本过滤
        val searchText = binding.searchEditText.text?.toString()?.trim() ?: ""
        if (searchText.isNotEmpty()) {
            filteredCounters = filteredCounters.filter { counterName ->
                counterName.contains(searchText, ignoreCase = true)
            }.toMutableList()
        }

        adapter.updateCounters(filteredCounters)
    }

    private class CounterListAdapter(
        private var counters: List<String>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<CounterListAdapter.ViewHolder>() {

        fun updateCounters(newCounters: List<String>) {
            counters = newCounters
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            val textView = view.findViewById<TextView>(android.R.id.text1)
            textView.setTextColor(parent.context.getColor(android.R.color.white))
            textView.setPadding(16, 16, 16, 16)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val counterName = counters[position]
            holder.textView.text = counterName
            holder.itemView.setOnClickListener {
                onItemClick(counterName)
            }
        }

        override fun getItemCount(): Int = counters.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(android.R.id.text1)
        }
    }
}

private const val PREFS_NAME = "org.kde.bettercounter.ui.WidgetProvider"
private const val PREF_PREFIX_KEY = "appwidget_"

internal fun saveWidgetCounterNamePref(context: Context, appWidgetId: Int, counterName: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
    prefs.putString(PREF_PREFIX_KEY + appWidgetId, counterName)
    prefs.apply()
}

internal fun loadWidgetCounterNamePref(context: Context, appWidgetId: Int): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
        ?: throw NoSuchElementException("Counter preference not found for widget id: $appWidgetId")
}

internal fun deleteWidgetCounterNamePref(context: Context, appWidgetId: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
    prefs.remove(PREF_PREFIX_KEY + appWidgetId)
    prefs.apply()
}

internal fun existsWidgetCounterNamePref(context: Context, appWidgetId: Int): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.contains(PREF_PREFIX_KEY + appWidgetId)
}
