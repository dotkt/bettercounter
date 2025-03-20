package org.kde.bettercounter

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kde.bettercounter.boilerplate.AppDatabase
import org.kde.bettercounter.persistence.CounterColor
import org.kde.bettercounter.persistence.CounterMetadata
import org.kde.bettercounter.persistence.CounterSummary
import org.kde.bettercounter.persistence.Entry
import org.kde.bettercounter.persistence.Interval
import org.kde.bettercounter.persistence.Repository
import org.kde.bettercounter.persistence.Tutorial
import java.io.InputStream
import java.io.OutputStream
import java.util.Calendar
import java.util.Date
import kotlin.collections.set
import android.media.MediaPlayer
import org.kde.bettercounter.persistence.Group
import org.kde.bettercounter.widget.WidgetUpdateManager

private const val TAG = "ViewModel"

class ViewModel(application: Application) {
    private val appContext: Context = application.applicationContext
    private var mediaPlayer: MediaPlayer? = null

    interface CounterObserver {
        fun onInitialCountersLoaded()
        fun onCounterAdded(counterName: String)
        fun onCounterRemoved(counterName: String)
        fun onCounterRenamed(oldName: String, newName: String)
        fun onCounterDecremented(counterName: String, oldEntryDate: Date)
    }

    private val repo: Repository
    private val counterObservers = HashSet<CounterObserver>()
    private val summaryMap = HashMap<String, MutableLiveData<CounterSummary>>()

    private var initialized = false

    init {
        val db = AppDatabase.getInstance(application)
        val prefs = application.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        repo = Repository(application, db.entryDao(), prefs)
        val initialCounters = repo.getCounterList()
        for (name in initialCounters) {
            summaryMap[name] = MutableLiveData()
        }
        CoroutineScope(Dispatchers.IO).launch {
            val counters = mutableListOf<CounterSummary>()
            for (name in initialCounters) {
                counters.add(repo.getCounterSummary(name))
            }
            withContext(Dispatchers.Main) {
                for (counter in counters) {
                    summaryMap[counter.name]!!.value = counter
                }
                synchronized(this) {
                    for (observer in counterObservers) {
                        observer.onInitialCountersLoaded()
                    }
                    initialized = true
                }
            }
        }
    }

    @MainThread
    fun observeCounterChange(observer: CounterObserver) {
        Log.d(TAG, "observeCounterChange SIZE" + counterObservers.size)
        synchronized(this) {
            counterObservers.add(observer)
            if (initialized) {
                observer.onInitialCountersLoaded()
            }
        }
    }

    fun addCounter(counter: CounterMetadata) {
        val name = counter.name
        repo.setCounterList(repo.getCounterList().toMutableList() + name)
        repo.setCounterMetadata(counter)
        repo.setCounterGroup(counter.name, counter.groupId)
        CoroutineScope(Dispatchers.IO).launch {
            summaryMap[name] = MutableLiveData(repo.getCounterSummary(name))
            withContext(Dispatchers.Main) {
                for (observer in counterObservers) {
                    observer.onCounterAdded(name)
                }
            }
        }
    }

    fun removeCounterChangeObserver(observer: CounterObserver) {
        counterObservers.remove(observer)
    }

    fun incrementCounter(name: String, date: Date = Calendar.getInstance().time) {
        CoroutineScope(Dispatchers.IO).launch {
            repo.addEntry(name, date)
            withContext(Dispatchers.Main) {
                playDingSound()
            }
            summaryMap[name]?.postValue(repo.getCounterSummary(name))
            // Switch to Main thread to play the sound
            
            // 增加计数后更新Widget
            WidgetUpdateManager.updateAllWidgets(appContext)
        }
    }

    private fun initMediaPlayer() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(appContext, R.raw.ding)
            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
                mediaPlayer = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPlayer: ${e.message}")
            mediaPlayer = null
        }
    }

    private fun playDingSound() {
        try {
            if (mediaPlayer == null) {
                initMediaPlayer()
            }
            mediaPlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                } else {
                    // 如果正在播放，重新初始化并播放
                    initMediaPlayer()
                    mediaPlayer?.start()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play sound: ${e.message}")
            // 出错时尝试重新初始化
            initMediaPlayer()
        }
    }

    fun incrementCounterWithCallback(name: String, date: Date = Calendar.getInstance().time, callback: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            repo.addEntry(name, date)
            withContext(Dispatchers.Main) {
                playDingSound()
            }
            summaryMap[name]?.postValue(repo.getCounterSummary(name))
            CoroutineScope(Dispatchers.Main).launch {
                callback()
            }
            
            // 增加计数后更新Widget
            WidgetUpdateManager.updateAllWidgets(appContext)
        }
    }

    fun decrementCounter(name: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val oldEntryDate = repo.removeEntry(name)
            summaryMap[name]?.postValue(repo.getCounterSummary(name))
            if (oldEntryDate != null) {
                for (observer in counterObservers) {
                    //return 0
                    //observer.onCounterDecremented(name, oldEntryDate)
                }
            }
            
            // 减少计数后更新Widget
            WidgetUpdateManager.updateAllWidgets(appContext)
        }
    }

    fun setTutorialShown(id: Tutorial) {
        repo.setTutorialShown(id)
    }

    fun resetTutorialShown(id: Tutorial) {
        repo.resetTutorialShown(id)
    }

    fun isTutorialShown(id: Tutorial): Boolean {
        return repo.isTutorialShown(id)
    }

    fun editCounterSameName(counterMetadata: CounterMetadata) {
        val name = counterMetadata.name
        repo.setCounterMetadata(counterMetadata)
        CoroutineScope(Dispatchers.IO).launch {
            summaryMap[name]?.postValue(repo.getCounterSummary(name))
        }
    }

    fun editCounter(oldName: String, counterMetadata: CounterMetadata) {
        val newName = counterMetadata.name
        repo.deleteCounterMetadata(oldName)
        repo.setCounterMetadata(counterMetadata)
        val list = repo.getCounterList().toMutableList()
        list[list.indexOf(oldName)] = newName
        repo.setCounterList(list)

        CoroutineScope(Dispatchers.IO).launch {
            repo.renameCounter(oldName, newName)
            val counter: MutableLiveData<CounterSummary>? = summaryMap.remove(oldName)
            if (counter == null) {
                Log.e(TAG, "Trying to rename a counter but the old counter doesn't exist")
                return@launch
            }
            summaryMap[newName] = counter
            counter.postValue(repo.getCounterSummary(newName))
            withContext(Dispatchers.Main) {
                for (observer in counterObservers) {
                    observer.onCounterRenamed(oldName, newName)
                }
            }
        }
    }

    fun getCounterSummary(name: String): LiveData<CounterSummary> {
        return summaryMap[name]!!
    }

    fun counterExists(name: String): Boolean = repo.getCounterList().contains(name)

    fun getCounterList() = repo.getCounterList()

    fun saveCounterOrder(value: List<String>) = repo.setCounterList(value)

    fun resetCounter(name: String) {
        CoroutineScope(Dispatchers.IO).launch {
            repo.removeAllEntries(name)
            summaryMap[name]?.postValue(repo.getCounterSummary(name))
        }
    }

    fun deleteCounter(name: String) {
        CoroutineScope(Dispatchers.IO).launch {
            repo.removeAllEntries(name)
            withContext(Dispatchers.Main) {
                for (observer in counterObservers) {
                    observer.onCounterRemoved(name)
                }
            }
        }
        summaryMap.remove(name)
        repo.deleteCounterMetadata(name)
        val list = repo.getCounterList().toMutableList()
        list.remove(name)
        repo.setCounterList(list)
    }

    fun exportAll(stream: OutputStream, progressHandler: Handler?) {
        fun sendProgress(progress: Int) {
            val message = Message()
            message.arg1 = progress
            message.arg2 = repo.getCounterList().size
            progressHandler?.sendMessage(message)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val writer = stream.bufferedWriter()
                for ((i, name) in repo.getCounterList().withIndex()) {
                    sendProgress(i)
                    val entries = repo.getAllEntriesSortedByDate(name)
                    writer.write(name)
                    for (entry in entries) {
                        writer.write(",")
                        writer.write(entry.date.time.toString())
                    }
                    writer.write("\n")
                    writer.flush()  // 每写完一个计数器就刷新缓冲区
                }
                sendProgress(repo.getCounterList().size)
                writer.flush()  // 最后再次刷新确保所有数据都写入
                withContext(Dispatchers.Main) {
                    writer.close()  // 在主线程中关闭流
                    stream.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Export failed: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    try {
                        stream.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to close stream: ${e.message}")
                    }
                }
            }
        }
    }

    fun importAll(context: Context, stream: InputStream, progressHandler: Handler?) {
        fun sendProgress(progress: Int, done: Int) {
            val message = Message()
            message.arg1 = progress
            message.arg2 = done // -1 -> error, 0 -> wip, 1 -> done
            progressHandler?.sendMessage(message)
        }
        CoroutineScope(Dispatchers.IO).launch {
            stream.use { stream ->
                // We read everything into memory before we update the DB so we know there are no errors
                val namesToImport: MutableList<String> = mutableListOf()
                val entriesToImport: MutableList<Entry> = mutableListOf()
                try {
                    stream.bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            parseImportLine(line, namesToImport, entriesToImport)
                            sendProgress(namesToImport.size, 0)
                        }
                    }
                    val reusedCounterMetadata = CounterMetadata("", Interval.DEFAULT, 0, CounterColor.getDefault(context))
                    namesToImport.forEach { name ->
                        if (!counterExists(name)) {
                            reusedCounterMetadata.name = name
                            addCounter(reusedCounterMetadata)
                        }
                    }
                    repo.bulkAddEntries(entriesToImport)
                    sendProgress(namesToImport.size, 1)
                } catch (e: Exception) {
                    e.printStackTrace()
                    sendProgress(namesToImport.size, -1)
                }
            }
        }
    }

    fun getEntriesForRangeSortedByDate(name: String, since: Date, until: Date): LiveData<List<Entry>> {
        val ret = MutableLiveData<List<Entry>>()
        CoroutineScope(Dispatchers.IO).launch {
            val entries = repo.getEntriesForRangeSortedByDate(name, since, until)
            //Log.e(TAG, "Queried ${entries.size} entries")
            CoroutineScope(Dispatchers.Main).launch {
                ret.value = entries
            }
        }
        return ret
    }

    fun refreshCounterSummary(name: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val summary = repo.getCounterSummary(name)
            summaryMap[name]?.postValue(summary)
        }
    }

    suspend fun refreshAllObservers() {
        val counterList = repo.getCounterList()
        for (name in counterList) {
            try {
                val summary = repo.getCounterSummary(name)
                summaryMap[name]?.postValue(summary)
            } catch (e: Exception) {
                Log.e(TAG, "刷新计数器 $name 时出错: ${e.message}")
            }
        }
    }

    fun getGroups(): List<Group> {
        return repo.getGroups()
    }

    fun addGroup(name: String) {
        val groups = repo.getGroups().toMutableList()
        val newId = "group_${System.currentTimeMillis()}"
        groups.add(Group(newId, name, groups.size))
        repo.saveGroups(groups)
    }

    fun renameGroup(groupId: String, newName: String) {
        val groups = repo.getGroups().toMutableList()
        val group = groups.find { it.id == groupId }
        if (group != null) {
            group.name = newName
            repo.saveGroups(groups)
        }
    }

    fun deleteGroup(groupId: String) {
        if (groupId == "default") return // 默认分组不能删除
        
        val groups = repo.getGroups().toMutableList()
        groups.removeIf { it.id == groupId }
        repo.saveGroups(groups)
        
        // 将此分组中的计数器移动到默认分组
        val counterList = repo.getCounterList()
        for (counterName in counterList) {
            if (repo.getCounterGroup(counterName) == groupId) {
                repo.setCounterGroup(counterName, "default")
            }
        }
    }

    fun getCounterGroup(counterName: String): String {
        return repo.getCounterGroup(counterName)
    }

    fun setCounterGroup(counterName: String, groupId: String) {
        repo.setCounterGroup(counterName, groupId)
        // 更新LiveData
        CoroutineScope(Dispatchers.IO).launch {
            summaryMap[counterName]?.postValue(repo.getCounterSummary(counterName))
        }
    }

    fun getCounterValue(counterName: String): Int {
        // 获取CounterSummary对象
        val summary = getCounterSummary(counterName).value ?: return 0
        
        try {
            // 尝试使用getFormattedCount方法获取格式化的计数
            val method = CounterSummary::class.java.getDeclaredMethod("getFormattedCount", Boolean::class.java)
            method.isAccessible = true
            val formattedCount = method.invoke(summary, true) as? String
            
            // 从格式化的计数中提取数字
            if (formattedCount != null) {
                android.util.Log.d("ViewModel", "格式化计数: $formattedCount")
                val number = formattedCount.filter { it.isDigit() }
                if (number.isNotEmpty()) {
                    return number.toInt()
                }
            }
            
            // 回退到使用字段访问
            val fields = CounterSummary::class.java.declaredFields
            for (field in fields) {
                if (field.name.contains("count", ignoreCase = true) || 
                    field.name == "total" || 
                    field.name == "value") {
                    field.isAccessible = true
                    val value = field.get(summary)
                    if (value is Int) {
                        return value
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ViewModel", "获取计数失败: ${e.message}")
        }
        
        return 0
    }

    companion object {
        fun parseImportLine(line: String, namesToImport: MutableList<String>, entriesToImport: MutableList<Entry>) {
            val nameAndDates = line.splitToSequence(",").iterator()
            var name = nameAndDates.next()
            var nameEnded = false
            nameAndDates.forEach { timestamp ->
                // Hack to support counters with commas in their names
                val timestampLong = if (nameEnded) {
                    timestamp.toLong()
                } else {
                    val maybeTimestamp = timestamp.toLongOrNull()
                    if (maybeTimestamp == null || maybeTimestamp < 100000000000L) {
                        name += ",$timestamp"
                        return@forEach
                    }
                    nameEnded = true
                    maybeTimestamp
                }
                entriesToImport.add(Entry(name = name, date = Date(timestampLong)))
            }
            namesToImport.add(name)
        }
    }

}
