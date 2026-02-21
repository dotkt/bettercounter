package org.kde.bettercounter.persistence

import android.content.Context
import android.content.SharedPreferences
import org.kde.bettercounter.BuildConfig
import org.kde.bettercounter.boilerplate.Converters
import org.kde.bettercounter.extensions.addInterval
import org.kde.bettercounter.extensions.copy
import org.kde.bettercounter.extensions.truncate
import java.util.Calendar
import java.util.Date

const val alwaysShowTutorialsInDebugBuilds = false

const val COUNTERS_PREFS_KEY = "counters"
const val COUNTERS_INTERVAL_PREFS_KEY = "interval.%s"
const val COUNTERS_COLOR_PREFS_KEY = "color.%s"
const val COUNTERS_GOAL_PREFS_KEY = "goal.%s"
const val COUNTERS_CATEGORY_PREFS_KEY = "category.%s"
const val COUNTERS_TYPE_PREFS_KEY = "type.%s"
const val COUNTERS_FORMULA_PREFS_KEY = "formula.%s"
const val COUNTERS_STEP_PREFS_KEY = "step.%s"
const val TUTORIALS_PREFS_KEY = "tutorials"

class Repository(
    private val context: Context,
    private val entryDao: EntryDao,
    private val sharedPref: SharedPreferences
) {

    private var tutorials: MutableSet<String>
    private var counters: List<String>
    private var counterCache = HashMap<String, CounterSummary>()

    init {
        val countersStr = sharedPref.getString(COUNTERS_PREFS_KEY, "[]")
        counters = Converters.stringToStringList(countersStr)
        tutorials = sharedPref.getStringSet(TUTORIALS_PREFS_KEY, setOf())!!.toMutableSet()
        if (BuildConfig.DEBUG && alwaysShowTutorialsInDebugBuilds) {
            tutorials = mutableSetOf()
        }
    }

    fun getCounterList(): List<String> {
        return counters
    }

    fun setCounterList(list: List<String>) {
        // 验证：检查是否有重复的计数器名称
        val duplicates = list.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicates.isNotEmpty()) {
            android.util.Log.e("Repository", "setCounterList: 发现重复的计数器名称: ${duplicates.keys.joinToString()}")
            // 去重，保留第一次出现的
            val uniqueList = list.distinct()
            android.util.Log.w("Repository", "setCounterList: 去重后列表大小: ${uniqueList.size} (原: ${list.size})")
            val jsonStr = Converters.stringListToString(uniqueList)
            sharedPref.edit().putString(COUNTERS_PREFS_KEY, jsonStr).apply()
            counters = uniqueList
        } else {
            val jsonStr = Converters.stringListToString(list)
            sharedPref.edit().putString(COUNTERS_PREFS_KEY, jsonStr).apply()
            counters = list
        }
    }

    fun setTutorialShown(id: Tutorial) {
        tutorials.add(id.name)
        sharedPref.edit().putStringSet(TUTORIALS_PREFS_KEY, tutorials).apply()
    }

    fun resetTutorialShown(id: Tutorial) {
        tutorials.remove(id.name)
        sharedPref.edit().putStringSet(TUTORIALS_PREFS_KEY, tutorials).apply()
    }

    fun isTutorialShown(id: Tutorial): Boolean {
        return tutorials.contains(id.name)
    }

    private fun getCounterColor(name: String): CounterColor {
        val key = COUNTERS_COLOR_PREFS_KEY.format(name)
        return CounterColor(sharedPref.getInt(key, CounterColor.getDefault(context).colorInt))
    }

    private fun getCounterInterval(name: String): Interval {
        val key = COUNTERS_INTERVAL_PREFS_KEY.format(name)
        val str = sharedPref.getString(key, null)
        return when (str) {
            "YTD" -> Interval.YEAR
            null -> Interval.DEFAULT
            else -> Interval.valueOf(str)
        }
    }

    private fun getCounterGoal(name: String): Int {
        val key = COUNTERS_GOAL_PREFS_KEY.format(name)
        return sharedPref.getInt(key, 0)
    }

    fun getCounterCategory(name: String): String {
        val key = COUNTERS_CATEGORY_PREFS_KEY.format(name)
        return sharedPref.getString(key, "默认") ?: "默认"
    }

    private fun getCounterType(name: String): CounterType {
        val key = COUNTERS_TYPE_PREFS_KEY.format(name)
        return try {
            CounterType.valueOf(sharedPref.getString(key, CounterType.STANDARD.name)!!)
        } catch (e: Exception) {
            CounterType.STANDARD
        }
    }

    private fun getCounterFormula(name: String): String? {
        val key = COUNTERS_FORMULA_PREFS_KEY.format(name)
        return sharedPref.getString(key, null)
    }

    private fun getCounterStep(name: String): Int {
        val key = COUNTERS_STEP_PREFS_KEY.format(name)
        return sharedPref.getInt(key, 1)
    }


    fun deleteCounterMetadata(name: String) {
        val colorKey = COUNTERS_COLOR_PREFS_KEY.format(name)
        val intervalKey = COUNTERS_INTERVAL_PREFS_KEY.format(name)
        val goalKey = COUNTERS_GOAL_PREFS_KEY.format(name)
        val categoryKey = COUNTERS_CATEGORY_PREFS_KEY.format(name)
        val typeKey = COUNTERS_TYPE_PREFS_KEY.format(name)
        val formulaKey = COUNTERS_FORMULA_PREFS_KEY.format(name)
        val stepKey = COUNTERS_STEP_PREFS_KEY.format(name)
        sharedPref.edit()
            .remove(colorKey)
            .remove(intervalKey)
            .remove(goalKey)
            .remove(categoryKey)
            .remove(typeKey)
            .remove(formulaKey)
            .remove(stepKey)
            .apply()
        counterCache.remove(name)
    }

    fun setCounterMetadata(counter: CounterMetadata) {
        val colorKey = COUNTERS_COLOR_PREFS_KEY.format(counter.name)
        val intervalKey = COUNTERS_INTERVAL_PREFS_KEY.format(counter.name)
        val goalKey = COUNTERS_GOAL_PREFS_KEY.format(counter.name)
        val categoryKey = COUNTERS_CATEGORY_PREFS_KEY.format(counter.name)
        val typeKey = COUNTERS_TYPE_PREFS_KEY.format(counter.name)
        val formulaKey = COUNTERS_FORMULA_PREFS_KEY.format(counter.name)
        val stepKey = COUNTERS_STEP_PREFS_KEY.format(counter.name)

        val editor = sharedPref.edit()
            .putInt(colorKey, counter.color.colorInt)
            .putString(intervalKey, counter.interval.toString())
            .putInt(goalKey, counter.goal)
            .putString(categoryKey, counter.category)
            .putString(typeKey, counter.type.name)
            .putInt(stepKey, counter.step)

        if (counter.formula != null) {
            editor.putString(formulaKey, counter.formula)
        } else {
            editor.remove(formulaKey)
        }

        editor.apply()
        counterCache.remove(counter.name)
    }

    suspend fun getCounterSummary(name: String): CounterSummary {
        val interval = getCounterInterval(name)
        val color = getCounterColor(name)
        val goal = getCounterGoal(name)
        val category = getCounterCategory(name)
        val type = getCounterType(name)
        val formula = getCounterFormula(name)
        val step = getCounterStep(name)

        val intervalStartDate = when (interval) {
            Interval.LIFETIME -> Calendar.getInstance().apply { set(Calendar.YEAR, 1990) }
            else -> Calendar.getInstance().apply { truncate(interval) }
        }
        val intervalEndDate = intervalStartDate.copy().apply { addInterval(interval, 1) }
        val firstLastAndCount = entryDao.getFirstLastAndCount(name)

        // 每次都重新计算计数器摘要，不使用缓存
        return CounterSummary(
            name = name,
            color = color,
            interval = interval,
            goal = goal,
            category = category,
            lastIntervalCount = entryDao.getCountInRange(name, intervalStartDate.time, intervalEndDate.time),
            totalCount = firstLastAndCount.count,
            leastRecent = firstLastAndCount.first,
            mostRecent = firstLastAndCount.last,
            type = type,
            formula = formula,
            step = step
        )
    }

    suspend fun renameCounter(oldName: String, newName: String) {
        entryDao.renameCounter(oldName, newName)
        counterCache.remove(oldName)
    }

    suspend fun addEntry(name: String, date: Date = Calendar.getInstance().time) {
        entryDao.insert(Entry(name = name, date = date))
        counterCache.remove(name)
    }

    suspend fun removeEntry(name: String): Date? {
        val entry = entryDao.getLastAdded(name)
        if (entry != null) {
            entryDao.delete(entry)
        }
        counterCache.remove(name)
        return entry?.date
    }

    suspend fun removeEntryIfWithinTimeLimit(name: String, timeLimitMillis: Long): Boolean {
        val entry = entryDao.getLastAdded(name)
        if (entry != null && System.currentTimeMillis() - entry.date.time <= timeLimitMillis) {
            entryDao.delete(entry)
            counterCache.remove(name)
            return true
        }
        return false
    }

    suspend fun removeAllEntries(name: String) {
        entryDao.deleteAll(name)
        counterCache.remove(name)
    }

    suspend fun getEntriesForRangeSortedByDate(name: String, since: Date, until: Date): List<Entry> {
        return entryDao.getAllEntriesInRangeSortedByDate(name, since, until)
    }
    fun getAllCategories(): Set<String> {
        val categories = mutableSetOf<String>()
        counters.forEach { counterName ->
            val category = getCounterCategory(counterName)
            categories.add(category)
        }
        return categories
    }

    suspend fun getAllEntriesSortedByDate(name: String): List<Entry> {
        return entryDao.getAllEntriesSortedByDate(name)
    }

    suspend fun bulkAddEntries(entries: List<Entry>) {
        val trackingCounter = "泽熙刷牙"
        val trackingEntries = entries.filter { it.name == trackingCounter }
        if (trackingEntries.isNotEmpty()) {
            android.util.Log.d("Repository", "[导入追踪] 泽熙刷牙: bulkAddEntries 被调用，准备插入 ${trackingEntries.size} 个条目")
            trackingEntries.forEachIndexed { index, entry ->
                android.util.Log.d("Repository", "[导入追踪] 泽熙刷牙: 第${index+1}个条目，时间戳=${entry.date.time}")
            }
        }
        
        entryDao.bulkInsert(entries)
        
        if (trackingEntries.isNotEmpty()) {
            val afterInsert = entryDao.getAllEntriesSortedByDate(trackingCounter).size
            android.util.Log.d("Repository", "[导入追踪] 泽熙刷牙: bulkInsert 完成后，数据库中实际有 $afterInsert 个条目（准备插入 ${trackingEntries.size} 个）")
            if (afterInsert != trackingEntries.size) {
                android.util.Log.e("Repository", "[导入追踪] 泽熙刷牙: 数据丢失！准备插入 ${trackingEntries.size} 个，但数据库中只有 $afterInsert 个")
            }
        }
        
        counterCache.clear() // we don't know what changed
    }
}
