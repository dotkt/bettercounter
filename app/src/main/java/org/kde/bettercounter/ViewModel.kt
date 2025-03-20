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
                    if (counterEntries.isEmpty()) continue

                    // 提取颜色整数值
                    val colorInt = extractColorInt(summary.color)
                    
                    // 将颜色转换为RGB格式和颜色名称
                    val colorRGB = intToRGBString(colorInt)
                    val colorName = getClosestColorName(colorInt)
                    
                    Log.d(TAG, "导出计数器: $counterName, 颜色RGB: $colorRGB, 颜色名称: $colorName")
                    
                    // 创建JSON，同时包含颜色名称和RGB格式
                    val configJson = """{"name":"$counterName","color":"$colorRGB","colorName":"$colorName","interval":"${summary.interval}","goal":${summary.goal}}"""
                    
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
                    stream.bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            parseImportLineWithJSON(line, namesToImport, entriesToImport, metadataToUpdate, context)
                            sendProgress(namesToImport.size, 0)
                        }
                    }
                    
                    // 处理计数器和元数据
                    namesToImport.forEach { name ->
                        val metadata = metadataToUpdate[name]
                        if (metadata != null) {
                            if (!counterExists(name)) {
                                // 新计数器，添加
                                addCounter(metadata)
                            } else {
                                // 现有计数器，强制更新所有配置
                                Log.d(TAG, "更新现有计数器: $name，应用导入的设置")
                                // 保存现有计数器的条目
                                val entries = repo.getAllEntriesSortedByDate(name)
                                // 删除现有计数器
                                deleteCounter(name)
                                // 使用新配置创建计数器
                                addCounter(metadata)
                                // 恢复原有条目
                                entries.forEach { entry ->
                                    entriesToImport.add(entry)
                                }
                            }
                        } else if (!counterExists(name)) {
                            // 没有元数据但需要创建计数器
                            val defaultMetadata = CounterMetadata(
                                name, 
                                Interval.DEFAULT, 
                                0, 
                                CounterColor.getDefault(context)
                            )
                            addCounter(defaultMetadata)
                        }
                    }
                    
                    // 批量添加条目
                    repo.bulkAddEntries(entriesToImport)
                    
                    // 确保每个计数器的摘要都被正确初始化
                    // 这是防止闪退的关键步骤
                    namesToImport.forEach { name ->
                        if (summaryMap[name] == null) {
                            summaryMap[name] = MutableLiveData()
                        }
                        val summary = repo.getCounterSummary(name)
                        withContext(Dispatchers.Main) {
                            summaryMap[name]?.value = summary
                        }
                    }
                    
                    // 通知导入完成
                    sendProgress(namesToImport.size, 1)
                } catch (e: Exception) {
                    Log.e(TAG, "Import failed: ${e.message}")
                    e.printStackTrace()
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
                // 新格式: {"name":"名称","color":123,"interval":"DAILY","goal":5},[时间戳1,时间戳2,...]
                val jsonEnd = line.indexOf("},[")
                if (jsonEnd > 0) {
                    val jsonPart = line.substring(0, jsonEnd + 1)
                    val timestampsPart = line.substring(jsonEnd + 1)
                    
                    // 解析JSON部分
                    val configMap = parseJsonObject(jsonPart)
                    val name = configMap["name"] as? String ?: return
                    
                    // 处理颜色 - 支持多种格式
                    val colorValue = configMap["color"] ?: configMap["colorName"]
                    val colorInt = getColorIntFromValue(colorValue)
                    
                    // 使用颜色整数值创建CounterColor对象
                    val color = createCounterColorFromInt(colorInt, context ?: return)
                    
                    val intervalStr = configMap["interval"] as? String ?: Interval.DEFAULT.toString()
                    val interval = try {
                        Interval.valueOf(intervalStr)
                    } catch (e: Exception) {
                        Interval.DEFAULT
                    }
                    val goal = (configMap["goal"] as? Number)?.toInt() ?: 0
                    
                    // 创建元数据，使用颜色对象
                    val metadata = CounterMetadata(name, interval, goal, color)
                    metadataToUpdate[name] = metadata
                    
                    // 解析时间戳数组
                    parseTimestamps(timestampsPart, name, entriesToImport)
                    
                    if (!namesToImport.contains(name)) {
                        namesToImport.add(name)
                    }
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
            val cleaned = timestampsStr.trim().removePrefix("[").removeSuffix("]")
            if (cleaned.isEmpty()) return
            
            val timestamps = cleaned.split(",")
            for (timestamp in timestamps) {
                try {
                    val time = timestamp.trim().toLong()
                    entries.add(Entry(name = counterName, date = Date(time)))
                } catch (e: Exception) {
                    Log.w(TAG, "无法解析时间戳: $timestamp")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析时间戳数组失败: $timestampsStr", e)
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
        for ((name, summary) in summaryMap) {
            summary.postValue(repo.getCounterSummary(name))
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

}
