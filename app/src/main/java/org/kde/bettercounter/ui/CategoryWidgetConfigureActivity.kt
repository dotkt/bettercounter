package org.kde.bettercounter.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import org.kde.bettercounter.BetterApplication
import org.kde.bettercounter.R
import org.kde.bettercounter.ViewModel
import org.kde.bettercounter.databinding.WidgetCategoryConfigureBinding

class CategoryWidgetConfigureActivity : AppCompatActivity() {

    private lateinit var viewModel: ViewModel
    private lateinit var binding: WidgetCategoryConfigureBinding
    private lateinit var adapter: CategoryListAdapter

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = WidgetCategoryConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

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
        val allCategories = viewModel.getAllCategories().sorted()

        if (allCategories.isEmpty()) {
            Toast.makeText(this, R.string.no_counters, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.categoryNamesList.layoutManager = LinearLayoutManager(this)
        adapter = CategoryListAdapter(allCategories) { category ->
            saveWidgetCategoryPref(this, appWidgetId, category)
            val appWidgetManager = AppWidgetManager.getInstance(this)
            updateCategoryWidget(this, viewModel, appWidgetManager, appWidgetId)
            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
        binding.categoryNamesList.adapter = adapter
    }

    private class CategoryListAdapter(
        private val categories: List<String>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<CategoryListAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            val textView = view.findViewById<TextView>(android.R.id.text1)
            textView.setTextColor(parent.context.getColor(android.R.color.white))
            textView.setPadding(32, 32, 32, 32)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            holder.textView.text = category
            holder.itemView.setOnClickListener {
                onItemClick(category)
            }
        }

        override fun getItemCount(): Int = categories.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(android.R.id.text1)
        }
    }
}

private const val CATEGORY_PREFS_NAME = "org.kde.bettercounter.ui.CategoryWidgetProvider"
private const val CATEGORY_PREFIX_KEY = "appwidget_category_"

internal fun saveWidgetCategoryPref(context: Context, appWidgetId: Int, category: String) {
    val prefs = context.getSharedPreferences(CATEGORY_PREFS_NAME, Context.MODE_PRIVATE).edit()
    prefs.putString(CATEGORY_PREFIX_KEY + appWidgetId, category)
    prefs.apply()
}

internal fun loadWidgetCategoryPref(context: Context, appWidgetId: Int): String {
    val prefs = context.getSharedPreferences(CATEGORY_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(CATEGORY_PREFIX_KEY + appWidgetId, null) ?: ""
}

internal fun deleteWidgetCategoryPref(context: Context, appWidgetId: Int) {
    val prefs = context.getSharedPreferences(CATEGORY_PREFS_NAME, Context.MODE_PRIVATE).edit()
    prefs.remove(CATEGORY_PREFIX_KEY + appWidgetId)
    prefs.apply()
}

internal fun existsWidgetCategoryPref(context: Context, appWidgetId: Int): Boolean {
    val prefs = context.getSharedPreferences(CATEGORY_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.contains(CATEGORY_PREFIX_KEY + appWidgetId)
}
