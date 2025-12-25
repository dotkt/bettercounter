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
import android.graphics.Color

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
        synchronized(this) {
            for (name in initialCounters) {
                summaryMap[name] = MutableLiveData()
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            val counters = mutableListOf<CounterSummary>()
            for (name in initialCounters) {
                counters.add(repo.getCounterSummary(name))
            }
            withContext(Dispatchers.Main) {
                synchronized(this) {
                    for (counter in counters) {
                        summaryMap[counter.name]!!.value = counter
                    }
                    // 创建观察者的副本以避免并发修改
                    val observersCopy = counterObservers.toList()
                    for (observer in observersCopy) {
                        observer.onInitialCountersLoaded()
                    }
                    initialized = true
                }
            }
        }
    }

    @MainThread
    fun observeCounterChange(observer: CounterObserver) {
        synchronized(this) {
            Log.d(TAG, "observeCounterChange SIZE" + counterObservers.size)
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
        CoroutineScope(Dispatchers.IO).launch {
            val counterSummary = repo.getCounterSummary(name)
            synchronized(this) {
                summaryMap[name] = MutableLiveData(counterSummary)
            }
            withContext(Dispatchers.Main) {
                synchronized(this) {
                    val observersCopy = counterObservers.toList()
                    for (observer in observersCopy) {
                        observer.onCounterAdded(name)
                    }
                }
            }
        }
    }

    fun removeCounterChangeObserver(observer: CounterObserver) {
        synchronized(this) {
            counterObservers.remove(observer)
        }
    }

    fun incrementCounter(name: String, date: Date = Calendar.getInstance().time) {
        incrementCounterByValue(name, 1, date)
    }

    fun incrementCounterByValue(name: String, value: Int, date: Date = Calendar.getInstance().time) {
        CoroutineScope(Dispatchers.IO).launch {
            repeat(value) {
                repo.addEntry(name, date)
            }
            withContext(Dispatchers.Main) {
                playDingSound()
            }
            val counterSummary = repo.getCounterSummary(name)
            synchronized(this) {
                summaryMap[name]?.postValue(counterSummary)
            }
        }
    }

    fun decrementCounter(name: String) {
        decrementCounterByValue(name, 1)
    }

    fun decrementCounterByValue(name: String, value: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            repeat(value) {
                val oldEntryDate = repo.removeEntry(name)
                if (oldEntryDate != null) {
                    synchronized(this) {
                        val observersCopy = counterObservers.toList()
                        for (observer in observersCopy) {
                            observer.onCounterDecremented(name, oldEntryDate)
                        }
                    }
                }
            }
            val counterSummary = repo.getCounterSummary(name)
            synchronized(this) {
                summaryMap[name]?.postValue(counterSummary)
            }
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
            val counterSummary = repo.getCounterSummary(name)
            synchronized(this) {
                summaryMap[name]?.postValue(counterSummary)
            }
            CoroutineScope(Dispatchers.Main).launch {
                callback()
            }

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
            val counterSummary = repo.getCounterSummary(name)
            synchronized(this) {
                summaryMap[name]?.postValue(counterSummary)
            }
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
            val newCounterSummary = repo.getCounterSummary(newName)
            synchronized(this) {
                val counter: MutableLiveData<CounterSummary>? = summaryMap.remove(oldName)
                if (counter == null) {
                    Log.e(TAG, "Trying to rename a counter but the old counter doesn't exist")
                    return@launch
                }
                summaryMap[newName] = counter
                counter.postValue(newCounterSummary)
            }
            withContext(Dispatchers.Main) {
                synchronized(this) {
                    val observersCopy = counterObservers.toList()
                    for (observer in observersCopy) {
                        observer.onCounterRenamed(oldName, newName)
                    }
                }
            }
        }
    }

    fun getCounterSummary(name: String): LiveData<CounterSummary> {
        synchronized(this) {
            return summaryMap[name]!!
        }
    }

    fun counterExists(name: String): Boolean = repo.getCounterList().contains(name)

    fun getCounterList() = repo.getCounterList()

    fun getCounterCategory(name: String): String = repo.getCounterCategory(name)

    fun getAllCategories(): Set<String> = repo.getAllCategories()

    fun saveCounterOrder(value: List<String>) = repo.setCounterList(value)

    fun resetCounter(name: String) {
        CoroutineScope(Dispatchers.IO).launch {
            repo.removeAllEntries(name)
            val counterSummary = repo.getCounterSummary(name)
            synchronized(this) {
                summaryMap[name]?.postValue(counterSummary)
            }
        }
    }

    fun clearAllData() {
        CoroutineScope(Dispatchers.IO).launch {
            val counters = repo.getCounterList()
            for (counterName in counters) {
                repo.removeAllEntries(counterName)
                val counterSummary = repo.getCounterSummary(counterName)
                synchronized(this) {
                    summaryMap[counterName]?.postValue(counterSummary)
                }
            }
        }
    }

    fun deleteCounter(name: String) {
        CoroutineScope(Dispatchers.IO).launch {
            repo.removeAllEntries(name)
            withContext(Dispatchers.Main) {
                synchronized(this) {
                    val observersCopy = counterObservers.toList()
                    for (observer in observersCopy) {
                        observer.onCounterRemoved(name)
                    }
                }
            }
        }
        synchronized(this) {
            summaryMap.remove(name)
        }
        repo.deleteCounterMetadata(name)
        val list = repo.getCounterList().toMutableList()
        list.remove(name)
        repo.setCounterList(list)
    }

    fun exportAll(outputStream: OutputStream, progressHandler: Handler?, closeStreamWhenDone: Boolean = false) {
        fun sendProgress(progress: Int, total: Int, handler: Handler?) {
            val message = Message()
            message.arg1 = progress
            message.arg2 = total
            handler?.sendMessage(message)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val writer = outputStream.bufferedWriter()
            try {
                val counters = repo.getCounterList()
                var exported = 0

                for (counterName in counters) {
                    val summary = repo.getCounterSummary(counterName)
                    val counterEntries = repo.getAllEntriesSortedByDate(counterName)
                    Log.d(TAG, "[导出] 计数器: $counterName, 条目数: ${counterEntries.size}")
                    if (counterEntries.isEmpty()) continue

                    // 提取颜色整数值
                    val colorInt = extractColorInt(summary.color)
                    
                    // 将颜色转换为RGB格式和颜色名称
                    val colorRGB = intToRGBString(colorInt)
                    val colorName = getClosestColorName(colorInt)
                    
                    Log.d(TAG, "导出计数器: $counterName, 颜色RGB: $colorRGB, 颜色名称: $colorName")
                    
                    // 获取类别
                    val category = repo.getCounterCategory(counterName)
                    
                    // 创建JSON，同时包含颜色名称和RGB格式
                    val configJson = """{"name":"$counterName","color":"$colorRGB","colorName":"$colorName","interval":"${summary.interval}","goal":${summary.goal},"category":"$category"}"""
                    
                    // 创建时间戳部分
                    val timestamps = counterEntries.joinToString(",") { it.date.time.toString() }
                    
                    // 写入并刷新
                    writer.write("$configJson,[$timestamps]")
                    writer.newLine()
                    writer.flush()
                    
                    exported++
                    sendProgress(exported, counters.size, progressHandler)
                }
                
                writer.flush()
                
                if (closeStreamWhenDone) {
                    try {
                        writer.close()
                        outputStream.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "关闭流时出错: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Export failed: ${e.message}")
                try {
                    if (closeStreamWhenDone) {
                        outputStream.close()
                    }
                } catch (closeEx: Exception) {
                    // 忽略关闭异常
                }
                throw e
            }
        }
    }

    // 将整数颜色值转换为#RRGGBB格式
    private fun intToRGBString(colorInt: Int): String {
        val red = (colorInt shr 16) and 0xFF
        val green = (colorInt shr 8) and 0xFF
        val blue = colorInt and 0xFF
        return "#%02X%02X%02X".format(red, green, blue) 
    }

    // 获取最接近的标准颜色名称
    private fun getClosestColorName(colorInt: Int): String {
        // 定义标准颜色和名称的映射
        val colorMap = mapOf(
            0xFF000000.toInt() to "BLACK",
            0xFFFFFFFF.toInt() to "WHITE",
            0xFFFF0000.toInt() to "RED",
            0xFF00FF00.toInt() to "GREEN",
            0xFF0000FF.toInt() to "BLUE",
            0xFFFFFF00.toInt() to "YELLOW",
            0xFF800080.toInt() to "PURPLE",
            0xFFFFA500.toInt() to "ORANGE",
            0xFF00FFFF.toInt() to "CYAN",
            0xFFFFC0CB.toInt() to "PINK",
            0xFF808080.toInt() to "GRAY",
            0xFF8B4513.toInt() to "BROWN",
            0xFF2196F3.toInt() to "BLUE_MATERIAL"
        )
        
        // 获取红绿蓝分量
        val red = (colorInt shr 16) and 0xFF
        val green = (colorInt shr 8) and 0xFF
        val blue = colorInt and 0xFF
        
        // 找到最接近的标准颜色
        var closestColor = "CUSTOM"
        var minDistance = Int.MAX_VALUE
        
        for ((stdColorInt, name) in colorMap) {
            val stdRed = (stdColorInt shr 16) and 0xFF
            val stdGreen = (stdColorInt shr 8) and 0xFF
            val stdBlue = stdColorInt and 0xFF
            
            // 计算颜色距离（欧几里得距离）
            val distance = Math.sqrt(
                Math.pow((red - stdRed).toDouble(), 2.0) +
                Math.pow((green - stdGreen).toDouble(), 2.0) +
                Math.pow((blue - stdBlue).toDouble(), 2.0)
            ).toInt()
            
            if (distance < minDistance) {
                minDistance = distance
                closestColor = name
            }
        }
        
        return closestColor
    }

    // 从CounterColor对象中提取颜色整数值
    private fun extractColorInt(color: CounterColor): Int {
        // 解析颜色字符串，格式如: CounterColor(colorInt=-10341712)
        val colorStr = color.toString()
        return try {
            val startIndex = colorStr.indexOf("colorInt=") + 9
            val endIndex = colorStr.indexOf(")", startIndex)
            if (startIndex > 9 && endIndex > startIndex) {
                colorStr.substring(startIndex, endIndex).toInt()
            } else {
                0 // 默认值
            }
        } catch (e: Exception) {
            Log.e(TAG, "无法提取颜色值: $colorStr", e)
            0 // 默认值
        }
    }

    fun importAll(context: Context, stream: InputStream, progressHandler: Handler?) {
        fun sendProgress(progress: Int, done: Int) {
            val message = Message()
            message.arg1 = progress
            message.arg2 = done // -1->error, 0->wip, 1->done
            progressHandler?.sendMessage(message)
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            stream.use { stream ->
                val namesToImport = mutableListOf<String>()
                val entriesToImport = mutableListOf<Entry>()
                val metadataToUpdate = mutableMapOf<String, CounterMetadata>()
                
                try {
                    var hasValidLine = false
                    stream.bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            val lineTrimmed = line.trim()
                            if (lineTrimmed.isNotEmpty()) {
                                hasValidLine = true
                                parseImportLineWithJSON(lineTrimmed, namesToImport, entriesToImport, metadataToUpdate, context)
                                sendProgress(namesToImport.size, 0)
                            }
                        }
                    }
                    
                    // 验证文件格式：检查是否有有效数据
                    if (!hasValidLine) {
                        Log.e(TAG, "[导入] 文件为空或只包含空行")
                        sendProgress(0, -1)
                        return@launch
                    }
                    
                    if (namesToImport.isEmpty()) {
                        Log.e(TAG, "[导入] 文件格式无效：无法解析任何计数器")
                        sendProgress(0, -1)
                        return@launch
                    }
                    
                    Log.d(TAG, "[导入] 解析后待导入计数器: ${namesToImport.joinToString()}")
                    namesToImport.forEach { name ->
                        val count = entriesToImport.count { it.name == name }
                        Log.d(TAG, "[导入] 计数器: $name, 待导入条目数: $count")
                    }
                    
                    // 特别追踪"泽熙刷牙"计数器
                    val trackingCounterName = "泽熙刷牙"
                    val trackingEntriesBeforeProcessing = entriesToImport.count { it.name == trackingCounterName }
                    if (trackingEntriesBeforeProcessing > 0) {
                        Log.d(TAG, "[导入追踪] 泽熙刷牙: 处理计数器前，entriesToImport中有 $trackingEntriesBeforeProcessing 个条目")
                    }
                    
                    // 处理计数器和元数据
                    namesToImport.forEach { name ->
                        val metadata = metadataToUpdate[name]
                        val isTracking = name == trackingCounterName
                        
                        if (metadata != null) {
                            if (!counterExists(name)) {
                                // 新计数器，添加
                                if (isTracking) {
                                    Log.d(TAG, "[导入追踪] 泽熙刷牙: 是新计数器，直接添加")
                                }
                                addCounter(metadata)
                            } else {
                                // 现有计数器，强制更新所有配置
                                if (isTracking) {
                                    val existingCount = repo.getAllEntriesSortedByDate(name).size
                                    Log.d(TAG, "[导入追踪] 泽熙刷牙: 是现有计数器，现有条目数=$existingCount")
                                }
                                Log.d(TAG, "更新现有计数器: $name，应用导入的设置")
                                // 保存现有计数器的条目
                                val entries = repo.getAllEntriesSortedByDate(name)
                                if (isTracking) {
                                    Log.d(TAG, "[导入追踪] 泽熙刷牙: 保存了 ${entries.size} 个现有条目")
                                }
                                // 删除现有计数器
                                deleteCounter(name)
                                // 使用新配置创建计数器
                                addCounter(metadata)
                                // 恢复原有条目
                                entries.forEach { entry ->
                                    entriesToImport.add(entry)
                                }
                                if (isTracking) {
                                    val afterRestore = entriesToImport.count { it.name == trackingCounterName }
                                    Log.d(TAG, "[导入追踪] 泽熙刷牙: 恢复原有条目后，entriesToImport中有 $afterRestore 个条目")
                                }
                            }
                        } else if (!counterExists(name)) {
                            // 没有元数据但需要创建计数器
                            if (isTracking) {
                                Log.d(TAG, "[导入追踪] 泽熙刷牙: 没有元数据，创建默认计数器")
                            }
                            val defaultMetadata = CounterMetadata(
                                name, 
                                Interval.DEFAULT, 
                                0, 
                                CounterColor.getDefault(context),
                                "默认"
                            )
                            addCounter(defaultMetadata)
                        }
                    }
                    
                    // 特别追踪"泽熙刷牙"计数器
                    val trackingEntriesBefore = entriesToImport.count { it.name == trackingCounterName }
                    if (trackingEntriesBefore > 0) {
                        Log.d(TAG, "[导入追踪] 泽熙刷牙: 批量插入前，entriesToImport中有 $trackingEntriesBefore 个条目")
                        entriesToImport.filter { it.name == trackingCounterName }.forEachIndexed { index, entry ->
                            Log.d(TAG, "[导入追踪] 泽熙刷牙: 第${index+1}个条目，时间戳=${entry.date.time}")
                        }
                    }
                    
                    // 批量添加条目
                    repo.bulkAddEntries(entriesToImport)
                    Log.d(TAG, "[导入] 实际导入条目总数: ${entriesToImport.size}")
                    
                    // 记录导入的条目数
                    Log.d(TAG, "成功导入 ${entriesToImport.size} 个条目到 ${namesToImport.size} 个计数器")
                    
                    // 检查"泽熙刷牙"导入后的实际数量
                    if (trackingEntriesBefore > 0) {
                        val trackingEntriesAfter = repo.getAllEntriesSortedByDate(trackingCounterName).size
                        Log.d(TAG, "[导入追踪] 泽熙刷牙: 批量插入后，数据库中实际有 $trackingEntriesAfter 个条目（插入前准备 $trackingEntriesBefore 个）")
                        if (trackingEntriesAfter != trackingEntriesBefore) {
                            Log.e(TAG, "[导入追踪] 泽熙刷牙: 数据丢失！准备插入 $trackingEntriesBefore 个，但数据库中只有 $trackingEntriesAfter 个")
                        }
                    }

                    // 确保每个计数器的摘要都被正确初始化
                    namesToImport.forEach { name ->
                        if (summaryMap[name] == null) {
                            summaryMap[name] = MutableLiveData()
                        }
                        
                        val summary = repo.getCounterSummary(name)
                        Log.d(TAG, "刷新计数器: $name")
                        
                        // 使用 postValue 因为这是在 IO 线程中
                        synchronized(this) {
                            summaryMap[name]?.postValue(summary)
                        }
                    }
                    
                    // 强制刷新所有观察者
                    withContext(Dispatchers.Main) {
                        refreshLiveData()
                    }
                    
                    // 通知导入完成
                    namesToImport.forEach { name ->
                        val count = repo.getAllEntriesSortedByDate(name).size
                        Log.d(TAG, "[导入] 导入后计数器: $name, 条目数: $count")
                    }
                    sendProgress(namesToImport.size, 1)
                } catch (e: Exception) {
                    Log.e(TAG, "[导入] Import failed: ${e.javaClass.simpleName}: ${e.message}")
                    sendProgress(namesToImport.size, -1)
                }
            }
        }
    }

    // 解析带JSON配置的导入行
    private fun parseImportLineWithJSON(
        line: String,
        namesToImport: MutableList<String>,
        entriesToImport: MutableList<Entry>,
        metadataToUpdate: MutableMap<String, CounterMetadata>,
        context: Context? = null
    ) {
        if (line.isEmpty()) return
        
        try {
            // 检查是否包含JSON
            if (line.startsWith("{")) {
                // 新格式: {"name":"名称","color":"#RRGGBB","colorName":"RED","interval":"DAILY","goal":5},[时间戳1,时间戳2,...]
                // 尝试查找分隔符，支持 "},[" 和 "}, [" 两种格式
                var jsonEnd = line.indexOf("},[")
                var timestampsPart: String
                var jsonPart: String
                
                if (jsonEnd <= 0) {
                    // 尝试查找带空格的分隔符
                    jsonEnd = line.indexOf("}, [")
                    if (jsonEnd <= 0) {
                        Log.e(TAG, "[导入] JSON格式错误：找不到时间戳数组分隔符 '},[' 或 '}, ['，行: $line")
                        return
                    }
                    jsonPart = line.substring(0, jsonEnd + 1)
                    // jsonEnd 是 "}, [" 中 "}" 的位置，需要跳过 "}, [" 这4个字符
                    timestampsPart = line.substring(jsonEnd + 4).trim()
                } else {
                    jsonPart = line.substring(0, jsonEnd + 1)
                    // jsonEnd 是 "},[" 中 "}" 的位置，需要跳过 "},[" 这3个字符
                    timestampsPart = line.substring(jsonEnd + 3)
                }
                
                // 特别追踪"泽熙刷牙"计数器
                val isTrackingCounter = timestampsPart.contains("1746800049417") || line.contains("\"泽熙刷牙\"")
                if (isTrackingCounter) {
                    Log.d(TAG, "[导入追踪] 泽熙刷牙: 找到分隔符，jsonEnd=$jsonEnd, timestampsPart前50字符=${timestampsPart.take(50)}")
                }
                
                // 解析JSON部分
                val configMap = parseJsonObject(jsonPart)
                val name = configMap["name"] as? String
                if (name.isNullOrEmpty()) {
                    Log.e(TAG, "[导入] JSON格式错误：缺少name字段或name为空，行: $line")
                    return
                }
                
                // 处理颜色
                val colorValue = configMap["color"] ?: configMap["colorName"]
                val colorInt = getColorIntFromValue(colorValue)
                
                // 使用颜色整数值创建CounterColor对象
                val safeContext = context ?: return
                val color = createCounterColorFromInt(colorInt, safeContext)
                
                // 修复区间格式问题 - 支持DAY、DAILY等多种格式
                val intervalStr = (configMap["interval"] as? String)?.uppercase() ?: Interval.DEFAULT.toString()
                val interval = try {
                    when (intervalStr) {
                        "DAY" -> Interval.DAY
                        "WEEK" -> Interval.WEEK
                        "MONTH" -> Interval.MONTH
                        "YEAR" -> Interval.YEAR
                        else -> Interval.valueOf(intervalStr)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "无法识别的区间值: $intervalStr，使用默认值")
                    Interval.DEFAULT
                }
                
                val goal = (configMap["goal"] as? Number)?.toInt() ?: 0
                
                // 获取类别，如果没有则使用默认值
                val category = (configMap["category"] as? String)?.takeIf { it.isNotBlank() } ?: "默认"
                
                // 创建元数据
                val metadata = CounterMetadata(name, interval, goal, color, category)
                metadataToUpdate[name] = metadata
                
                Log.d(TAG, "导入计数器: $name, 颜色: $colorInt, 区间: $interval, 目标: $goal")
                
                // 解析时间戳数组
                parseTimestamps(timestampsPart, name, entriesToImport)
                
                if (!namesToImport.contains(name)) {
                    namesToImport.add(name)
                }
            } else {
                // 旧CSV格式: name,timestamp1,timestamp2,...
                val parts = line.split(",")
                if (parts.isNotEmpty()) {
                    val name = parts[0]
                    if (name.isNotEmpty()) {
                        for (i in 1 until parts.size) {
                            try {
                                val timestamp = parts[i].toLong()
                                entriesToImport.add(Entry(name = name, date = Date(timestamp)))
                            } catch (e: Exception) {
                                Log.w(TAG, "无法解析时间戳: ${parts[i]}")
                            }
                        }
                        
                        if (!namesToImport.contains(name)) {
                            namesToImport.add(name)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析行失败: $line", e)
        }
    }

    // 简单的JSON对象解析
    private fun parseJsonObject(jsonStr: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        try {
            // 移除大括号
            val content = jsonStr.trim().removePrefix("{").removeSuffix("}")
            
            // 分割键值对
            var inQuotes = false
            var start = 0
            val pairs = mutableListOf<String>()
            
            for (i in content.indices) {
                val c = content[i]
                if (c == '"') {
                    inQuotes = !inQuotes
                } else if (c == ',' && !inQuotes) {
                    pairs.add(content.substring(start, i).trim())
                    start = i + 1
                }
            }
            
            // 添加最后一个键值对
            if (start < content.length) {
                pairs.add(content.substring(start).trim())
            }
            
            // 解析每个键值对
            for (pair in pairs) {
                val colonPos = pair.indexOf(':')
                if (colonPos > 0) {
                    val key = pair.substring(0, colonPos).trim().removeSurrounding("\"")
                    val value = parseJsonValue(pair.substring(colonPos + 1).trim())
                    result[key] = value
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON解析错误: $jsonStr", e)
        }
        
        return result
    }

    // 解析JSON值
    private fun parseJsonValue(valueStr: String): Any {
        return when {
            valueStr.startsWith("\"") && valueStr.endsWith("\"") -> 
                valueStr.removeSurrounding("\"")
            valueStr == "true" -> true
            valueStr == "false" -> false
            valueStr == "null" -> ""
            valueStr.contains('.') -> try { valueStr.toDouble() } catch (e: Exception) { 0.0 }
            else -> try { valueStr.toInt() } catch (e: Exception) { 0 }
        }
    }

    // 解析时间戳数组
    private fun parseTimestamps(timestampsStr: String, counterName: String, entries: MutableList<Entry>) {
        try {
            var cleaned = timestampsStr.trim()
            
            // 特别追踪"泽熙刷牙"计数器
            val isTrackingCounter = counterName == "泽熙刷牙"
            
            if (isTrackingCounter) {
                Log.d(TAG, "[导入追踪] 泽熙刷牙: parseTimestamps 开始，原始字符串前100字符=${timestampsStr.take(100)}")
            }
            
            // 移除开头的 [ 和结尾的 ]
            if (cleaned.startsWith("[")) {
                cleaned = cleaned.removePrefix("[")
                if (isTrackingCounter) {
                    Log.d(TAG, "[导入追踪] 泽熙刷牙: 移除了开头的 [")
                }
            }
            if (cleaned.endsWith("]")) {
                cleaned = cleaned.removeSuffix("]")
                if (isTrackingCounter) {
                    Log.d(TAG, "[导入追踪] 泽熙刷牙: 移除了结尾的 ]")
                }
            }
            cleaned = cleaned.trim()
            
            if (cleaned.isEmpty()) {
                Log.d(TAG, "[导入] 计数器 $counterName 没有时间戳条目")
                return
            }
            
            val timestamps = cleaned.split(",")
            var parsedCount = 0
            var failedCount = 0
            
            if (isTrackingCounter) {
                Log.d(TAG, "[导入追踪] 泽熙刷牙: 清理后字符串前100字符=${cleaned.take(100)}")
                Log.d(TAG, "[导入追踪] 泽熙刷牙: 开始解析，时间戳字符串长度=${timestampsStr.length}, 清理后长度=${cleaned.length}, 分割后数量=${timestamps.size}")
                Log.d(TAG, "[导入追踪] 泽熙刷牙: 第一个分割结果=${timestamps.firstOrNull()}")
            }
            
            for ((index, timestamp) in timestamps.withIndex()) {
                try {
                    val time = timestamp.trim().toLong()
                    if (isTrackingCounter) {
                        Log.d(TAG, "[导入追踪] 泽熙刷牙: 第${index+1}/${timestamps.size}个时间戳=$time, 解析成功")
                    }
                    entries.add(Entry(name = counterName, date = Date(time)))
                    parsedCount++
                } catch (e: Exception) {
                    failedCount++
                    if (isTrackingCounter) {
                        Log.e(TAG, "[导入追踪] 泽熙刷牙: 第${index+1}/${timestamps.size}个时间戳解析失败: ${timestamp}", e)
                    } else {
                        Log.w(TAG, "[导入] 无法解析时间戳: ${timestamp}", e)
                    }
                }
            }
            
            if (isTrackingCounter) {
                Log.d(TAG, "[导入追踪] 泽熙刷牙: 解析完成，成功=$parsedCount, 失败=$failedCount, entries列表大小=${entries.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[导入] 解析时间戳数组失败: $timestampsStr", e)
        }
    }

    // 辅助方法
    private fun CounterColor.Companion.getDefaultInt(): Int {
        return 0xFF2196F3.toInt() // 默认蓝色
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

    suspend fun refreshAllObservers() {
        val entries: List<Pair<String, MutableLiveData<CounterSummary>>>
        synchronized(this) {
            entries = summaryMap.toList() // 创建副本避免并发修改
        }
        // 在同步块外获取所有计数器摘要
        val summaries = mutableListOf<Pair<String, CounterSummary>>()
        for ((name, _) in entries) {
            val counterSummary = repo.getCounterSummary(name) // IO线程
            summaries.add(Pair(name, counterSummary))
        }
        // 在主线程中更新LiveData
        withContext(Dispatchers.Main) {
            synchronized(this) {
                for ((name, counterSummary) in summaries) {
                    summaryMap[name]?.value = counterSummary
                }
            }
        }
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

    // 根据颜色名称获取CounterColor对象
    private fun getColorForName(colorName: String, context: Context): CounterColor {
        val defaultColor = CounterColor.getDefault(context)
        
        // 直接匹配颜色名称，不使用反射
        return when (colorName.uppercase()) {
            "BLACK" -> CounterColor.getDefault(context)
            "RED" -> CounterColor.getDefault(context) // 需要替换为实际的RED颜色
            "GREEN" -> CounterColor.getDefault(context) // 需要替换为实际的GREEN颜色
            "BLUE" -> CounterColor.getDefault(context) // 需要替换为实际的BLUE颜色
            "YELLOW" -> CounterColor.getDefault(context) // 需要替换为实际的YELLOW颜色
            "PURPLE" -> CounterColor.getDefault(context) // 需要替换为实际的PURPLE颜色
            "ORANGE" -> CounterColor.getDefault(context) // 需要替换为实际的ORANGE颜色
            "CYAN" -> CounterColor.getDefault(context) // 需要替换为实际的CYAN颜色
            "PINK" -> CounterColor.getDefault(context) // 需要替换为实际的PINK颜色
            "GRAY" -> CounterColor.getDefault(context) // 需要替换为实际的GRAY颜色
            else -> defaultColor
        }
    }

    // 获取颜色的英文名称
    private fun getColorName(color: CounterColor): String {
        // 尝试获取颜色名称
        return when (color.toString()) {
            "0" -> "BLACK"
            "1" -> "RED"
            "2" -> "GREEN"
            "3" -> "BLUE"
            "4" -> "YELLOW"
            "5" -> "PURPLE"
            "6" -> "ORANGE" 
            "7" -> "CYAN"
            "8" -> "PINK"
            "9" -> "GRAY"
            else -> "DEFAULT" // 如果无法识别，使用默认
        }
    }

    // 根据颜色值映射到名称
    private fun mapColorValueToName(colorValue: Int): String {
        // 通过颜色值的模式识别颜色
        return when (colorValue % 10) {
            0 -> "BLACK"
            1 -> "RED"
            2 -> "GREEN"
            3 -> "BLUE"
            4 -> "YELLOW"
            5 -> "PURPLE"
            6 -> "ORANGE" 
            7 -> "CYAN"
            8 -> "PINK"
            9 -> "GRAY"
            else -> "DEFAULT"
        }
    }

    // 创建具有指定colorInt的CounterColor对象
    private fun createCounterColorFromInt(colorInt: Int, context: Context): CounterColor {
        // 尝试使用反射动态设置颜色值
        val defaultColor = CounterColor.getDefault(context)
        
        return try {
            // 尝试使用构造函数
            val constructor = CounterColor::class.java.getDeclaredConstructor(Int::class.java)
            constructor.isAccessible = true
            constructor.newInstance(colorInt)
        } catch (e: Exception) {
            try {
                // 尝试通过Field设置值
                val field = CounterColor::class.java.getDeclaredField("colorInt")
                field.isAccessible = true
                field.set(defaultColor, colorInt)
                defaultColor
            } catch (e2: Exception) {
                Log.e(TAG, "无法创建指定颜色值的CounterColor: $colorInt", e2)
                defaultColor
            }
        }
    }

    // 根据名称获取颜色整数值
    private fun getColorIntForName(colorName: String): Int {
        return when (colorName.uppercase()) {
            "BLACK" -> -16777216 // Color.BLACK
            "RED" -> -65536 // Color.RED
            "GREEN" -> -16711936 // Color.GREEN
            "BLUE" -> -16776961 // Color.BLUE
            "YELLOW" -> -256 // Color.YELLOW
            "PURPLE" -> -8388480 // 紫色
            "ORANGE" -> -23296 // 橙色
            "CYAN" -> -16711681 // Color.CYAN
            "PINK" -> -65281 // 粉色
            "GRAY" -> -7829368 // Color.GRAY
            else -> 0 // 默认值
        }
    }

    // 获取颜色整数值，支持多种格式
    private fun getColorIntFromValue(colorValue: Any?): Int {
        return when (colorValue) {
            is Number -> colorValue.toInt()
            is String -> {
                val colorStr = colorValue.toString()
                when {
                    // RGB格式: #RRGGBB
                    colorStr.startsWith("#") && (colorStr.length == 7 || colorStr.length == 9) -> {
                        try {
                            Color.parseColor(colorStr)
                        } catch (e: Exception) {
                            getColorIntForName("DEFAULT")
                        }
                    }
                    // 数字字符串
                    colorStr.matches(Regex("-?\\d+")) -> {
                        try {
                            colorStr.toInt()
                        } catch (e: Exception) {
                            getColorIntForName("DEFAULT")
                        }
                    }
                    // 颜色名称
                    else -> {
                        getColorIntForName(colorStr)
                    }
                }
            }
            else -> getColorIntForName("DEFAULT")
        }
    }

    // 获取计数器的所有条目
    suspend fun getAllEntriesSortedByDate(name: String): List<Entry> {
        return repo.getAllEntriesSortedByDate(name)
    }

    // 检查是否有任何计数器包含条目
    suspend fun hasDataToExport(): Boolean {
        val counters = repo.getCounterList()
        for (name in counters) {
            if (repo.getAllEntriesSortedByDate(name).isNotEmpty()) {
                return true
            }
        }
        return false
    }

    // 强制刷新所有LiveData
    private fun refreshLiveData() {
        CoroutineScope(Dispatchers.IO).launch {
            val counters = repo.getCounterList()
            val summaries = mutableListOf<Pair<String, CounterSummary>>()
            for (name in counters) {
                val summary = repo.getCounterSummary(name)
                summaries.add(Pair(name, summary))
            }
            
            // 在主线程中更新LiveData
            withContext(Dispatchers.Main) {
                synchronized(this) {
                    for ((name, summary) in summaries) {
                        summaryMap[name]?.value = summary
                    }
                    // 通知所有观察者数据已更新
                    val observersCopy = counterObservers.toList()
                    for (observer in observersCopy) {
                        observer.onInitialCountersLoaded()
                    }
                }
            }
        }
    }

}
