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
const val TUTORIALS_PREFS_KEY = "tutorials"
const val GROUPS_PREFS_KEY = "groups"
const val COUNTER_GROUP_PREFS_KEY = "group.%s"

class Repository(
    private val context: Context,
    private val entryDao: EntryDao,
    private val sharedPref: SharedPreferences
) {

    private var tutorials: MutableSet<String>
    private var counters: List<String>
    private var counterCache = HashMap<String, CounterSummary>()
    private var groups: List<Group>

    init {
        val countersStr = sharedPref.getString(COUNTERS_PREFS_KEY, "[]")
        counters = Converters.stringToStringList(countersStr)
        tutorials = sharedPref.getStringSet(TUTORIALS_PREFS_KEY, setOf())!!.toMutableSet()
        if (BuildConfig.DEBUG && alwaysShowTutorialsInDebugBuilds) {
            tutorials = mutableSetOf()
        }

        // 初始化分组数据
        val groupsStr = sharedPref.getString(GROUPS_PREFS_KEY, "[{\"id\":\"default\",\"name\":\"默认\",\"order\":0}]")
        groups = Converters.stringToGroupList(groupsStr)
    }

    fun getCounterList(): List<String> {
        return counters
    }

    fun setCounterList(list: List<String>) {
        val jsonStr = Converters.stringListToString(list)
        sharedPref.edit().putString(COUNTERS_PREFS_KEY, jsonStr).apply()
        counters = list
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

    fun deleteCounterMetadata(name: String) {
        val colorKey = COUNTERS_COLOR_PREFS_KEY.format(name)
        val intervalKey = COUNTERS_INTERVAL_PREFS_KEY.format(name)
        val goalKey = COUNTERS_GOAL_PREFS_KEY.format(name)
        sharedPref.edit()
            .remove(colorKey)
            .remove(intervalKey)
            .remove(goalKey)
            .apply()
        counterCache.remove(name)
    }

    fun setCounterMetadata(counter: CounterMetadata) {
        val colorKey = COUNTERS_COLOR_PREFS_KEY.format(counter.name)
        val intervalKey = COUNTERS_INTERVAL_PREFS_KEY.format(counter.name)
        val goalKey = COUNTERS_GOAL_PREFS_KEY.format(counter.name)
        val groupKey = COUNTER_GROUP_PREFS_KEY.format(counter.name)
        sharedPref.edit()
            .putInt(colorKey, counter.color.colorInt)
            .putString(intervalKey, counter.interval.toString())
            .putInt(goalKey, counter.goal)
            .putString(groupKey, counter.groupId)
            .apply()
        counterCache.remove(counter.name)
    }

    fun getGroups(): List<Group> {
        return groups
    }

    fun saveGroups(groupList: List<Group>) {
        val jsonStr = Converters.groupListToString(groupList)
        sharedPref.edit().putString(GROUPS_PREFS_KEY, jsonStr).apply()
        groups = groupList
    }

    fun getCounterGroup(name: String): String {
        val key = COUNTER_GROUP_PREFS_KEY.format(name)
        return sharedPref.getString(key, "default") ?: "default"
    }

    fun setCounterGroup(name: String, groupId: String) {
        val key = COUNTER_GROUP_PREFS_KEY.format(name)
        sharedPref.edit().putString(key, groupId).apply()
        counterCache.remove(name)
    }

    suspend fun getCounterSummary(name: String): CounterSummary {
        val interval = getCounterInterval(name)
        val color = getCounterColor(name)
        val goal = getCounterGoal(name)
        val intervalStartDate = when (interval) {
            Interval.LIFETIME -> Calendar.getInstance().apply { set(Calendar.YEAR, 1990) }
            else -> Calendar.getInstance().apply { truncate(interval) }
        }
        val intervalEndDate = intervalStartDate.copy().apply { addInterval(interval, 1) }
        val firstLastAndCount = entryDao.getFirstLastAndCount(name)
        val groupId = getCounterGroup(name)
        
        // 安全获取时间段内计数
        val lastIntervalCount = if (interval == Interval.MYTIMER || interval == Interval.LIFETIME) {
            // 对于特殊间隔类型，返回总计数
            firstLastAndCount.count
        } else {
            // 对于普通间隔类型，使用正常的时间范围查询
            entryDao.getCountInRange(name, intervalStartDate.time, intervalEndDate.time)
        }
        
        return counterCache.getOrPut(name) {
            CounterSummary(
                name = name,
                color = color,
                interval = interval,
                goal = goal,
                lastIntervalCount = lastIntervalCount,
                totalCount = firstLastAndCount.count,
                leastRecent = firstLastAndCount.first,
                mostRecent = firstLastAndCount.last,
                groupId = groupId
            )
        }
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

    suspend fun removeAllEntries(name: String) {
        entryDao.deleteAll(name)
        counterCache.remove(name)
    }

    suspend fun getEntriesForRangeSortedByDate(name: String, since: Date, until: Date): List<Entry> {
        return entryDao.getAllEntriesInRangeSortedByDate(name, since, until)
    }
    suspend fun getAllEntriesSortedByDate(name: String): List<Entry> {
        return entryDao.getAllEntriesSortedByDate(name)
    }

    suspend fun bulkAddEntries(entries: List<Entry>) {
        entryDao.bulkInsert(entries)
        counterCache.clear() // we don't know what changed
    }
}
