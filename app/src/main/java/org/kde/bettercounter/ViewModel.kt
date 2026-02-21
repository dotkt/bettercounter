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
                // After initial load, calculate all dynamic counters to ensure they have a value.
                recalculateDynamicCounters()
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
            // Dynamic counters cannot be incremented directly
            val summary = summaryMap[name]?.value
            if (summary?.type == org.kde.bettercounter.persistence.CounterType.DYNAMIC) {
                Log.w(TAG, "Attempted to increment a dynamic counter '$name'. This is not allowed.")
                return@launch
            }

            if (value > 0) {
                repeat(value) {
                    repo.addEntry(name, date)
                }
            } else if (value < 0) {
                var removedCount = 0
                repeat(-value) {
                    if (repo.removeEntryIfWithinTimeLimit(name, 3600000L)) {
                        removedCount++
                    }
                }
                if (removedCount < -value && -value > 1) {
                    Log.d(TAG, "Requested to remove ${-value} entries, but only $removedCount were within the time limit.")
                }
            }
            
            withContext(Dispatchers.Main) {
                playDingSound()
            }
            val counterSummary = repo.getCounterSummary(name)
            synchronized(this) {
                summaryMap[name]?.postValue(counterSummary)
            }
            // After incrementing, recalculate any dynamic counters that might depend on this one
            recalculateDynamicCounters()
        }
    }

    fun decrementCounter(name: String, date: Date = Calendar.getInstance().time) {
        incrementCounterByValue(name, -1, date)
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

    fun incrementCounterWithCallback(name: String, step: Int = 1, date: Date = Calendar.getInstance().time, callback: () -> Unit) {
        val startTime = System.currentTimeMillis()
        Log.d("WidgetTimings", "incrementCounterWithCallback started for '$name' at $startTime")
        Log.d("DynamicCounterBug", "incrementCounterWithCallback triggered for '$name'")
        CoroutineScope(Dispatchers.IO).launch {
            incrementCounterByValue(name, step, date)
            withContext(Dispatchers.Main) {
                playDingSound()
            }
            val counterSummary = repo.getCounterSummary(name)
            synchronized(this) {
                summaryMap[name]?.postValue(counterSummary)
            }
            // After incrementing, recalculate any dynamic counters that might depend on this one
            recalculateDynamicCounters()
            CoroutineScope(Dispatchers.Main).launch {
                callback()
                val endTime = System.currentTimeMillis()
                Log.d("WidgetTimings", "incrementCounterWithCallback finished for '$name' in ${endTime - startTime}ms")
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
            // After editing, recalculate all dynamic counters as dependencies might have changed
            recalculateDynamicCounters()
        }
    }

    fun editCounter(oldName: String, counterMetadata: CounterMetadata) {
        val newName = counterMetadata.name
        repo.deleteCounterMetadata(oldName)
        repo.setCounterMetadata(counterMetadata)
        val list = repo.getCounterList().toMutableList()
        val index = list.indexOf(oldName)
        if (index != -1) {
            list[index] = newName
            repo.setCounterList(list)
        } else {
            Log.e(TAG, "editCounter: oldName '$oldName' not found in counter list, cannot rename")
            // 如果旧名称不在列表中，但新名称也不在，则添加新名称（可能是数据不一致的情况）
            if (!list.contains(newName)) {
                list.add(newName)
                repo.setCounterList(list)
                Log.w(TAG, "editCounter: Added newName '$newName' to counter list to recover from inconsistency")
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            repo.renameCounter(oldName, newName)
            // Also need to update formulas that refer to the old name
            updateFormulasOnRename(oldName, newName)
            val newCounterSummary = repo.getCounterSummary(newName)
            synchronized(this) {
                val counter: MutableLiveData<CounterSummary>? = summaryMap.remove(oldName)
                if (counter == null) {
                    Log.e(TAG, "Trying to rename a counter but the old counter doesn't exist")
                    // Still proceed to recalculate, as formulas might have changed
                    recalculateDynamicCounters()
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
            // After renaming, recalculate all dynamic counters
            recalculateDynamicCounters()
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
            val summary = summaryMap[name]?.value
            if (summary?.type == org.kde.bettercounter.persistence.CounterType.DYNAMIC) {
                Log.w(TAG, "Attempted to reset a dynamic counter '$name'. This is not allowed.")
                return@launch
            }
            repo.removeAllEntries(name)
            val counterSummary = repo.getCounterSummary(name)
            synchronized(this) {
                summaryMap[name]?.postValue(counterSummary)
            }
            // After resetting, recalculate any dynamic counters that might depend on this one
            recalculateDynamicCounters()
        }
    }

    fun clearAllData() {
        CoroutineScope(Dispatchers.IO).launch {
            val counters = repo.getCounterList()
            for (counterName in counters) {
                 val summary = summaryMap[counterName]?.value
                if (summary?.type == org.kde.bettercounter.persistence.CounterType.STANDARD) {
                    repo.removeAllEntries(counterName)
                    val counterSummary = repo.getCounterSummary(counterName)
                    synchronized(this) {
                        summaryMap[counterName]?.postValue(counterSummary)
                    }
                }
            }
            recalculateDynamicCounters()
        }
    }

    fun deleteCounter(name: String) {
        CoroutineScope(Dispatchers.IO).launch {
            // --- START OF DATA MUTATION ---
            // 1. Delete all data from the repository first.
            repo.removeAllEntries(name)
            repo.deleteCounterMetadata(name)
            val list = repo.getCounterList().toMutableList()
            list.remove(name)
            repo.setCounterList(list)

            // 2. Then, update the ViewModel's internal state to match.
            synchronized(this) {
                summaryMap.remove(name)
            }
            // --- END OF DATA MUTATION ---

            // 3. Now that the data model is fully consistent, notify the UI.
            withContext(Dispatchers.Main) {
                synchronized(this) {
                    val observersCopy = counterObservers.toList()
                    for (observer in observersCopy) {
                        observer.onCounterRemoved(name)
                    }
                }
            }

            // 4. Finally, perform follow-up calculations which affect other counters.
            updateFormulasOnDelete(name)
            recalculateDynamicCounters()
        }
    }

    // --- Dynamic Counter Logic ---

    internal fun recalculateDynamicCounters() {
        Log.d("DynamicCounterBug", "recalculateDynamicCounters started.")
        CoroutineScope(Dispatchers.IO).launch {
            val allSummaries = summaryMap.values.mapNotNull { it.value }
            val dynamicCounters = allSummaries.filter { it.type == org.kde.bettercounter.persistence.CounterType.DYNAMIC }

            if (dynamicCounters.isEmpty()) {
                Log.d("DynamicCounterBug", "No dynamic counters found to recalculate.")
                return@launch
            }
            Log.d("DynamicCounterBug", "Found dynamic counters to process: ${dynamicCounters.joinToString { it.name }}")

            val standardCounterValues = allSummaries
                .filter { it.type == org.kde.bettercounter.persistence.CounterType.STANDARD }
                .associate { it.name to it.lastIntervalCount }

            val categorySums = repo.getAllCategories().associateWith { category ->
                allSummaries.filter {
                    it.type == org.kde.bettercounter.persistence.CounterType.STANDARD && repo.getCounterCategory(it.name) == category
                }.sumOf { it.lastIntervalCount }
            }

            for (dynamicCounter in dynamicCounters) {
                val formula = dynamicCounter.formula
                if (formula.isNullOrBlank()) continue

                val result = FormulaEvaluator.evaluate(formula, standardCounterValues, categorySums)
                Log.d("DynamicCounterBug", "Recalculating '${dynamicCounter.name}' (Formula: $formula). Old value: ${dynamicCounter.lastIntervalCount}, New value: $result")
                if (dynamicCounter.lastIntervalCount != result) {
                    val oldValue = dynamicCounter.lastIntervalCount
                    dynamicCounter.lastIntervalCount = result
                    summaryMap[dynamicCounter.name]?.postValue(dynamicCounter)
                    Log.d("DynamicCounterBug", "Value changed for '${dynamicCounter.name}' from $oldValue to $result. Posting update.")
                }
            }
        }
    }
    
    fun validateFormula(
        formula: String,
        counterName: String // The name of the counter this formula belongs to, to check for self-reference
    ): FormulaValidationResult {
        // Must be called with up-to-date data
        val allCounters = repo.getCounterList()
        val allCategories = repo.getAllCategories()
        val dynamicCountersMap = summaryMap.values
            .mapNotNull { it.value }
            .filter { it.type == org.kde.bettercounter.persistence.CounterType.DYNAMIC }
            .associateBy({ it.name }, { it.formula ?: "" })

        return FormulaEvaluator.validate(formula, counterName, allCounters, allCategories, dynamicCountersMap)
    }

    private fun CoroutineScope.updateFormulasOnRename(oldName: String, newName: String) {
        val allSummaries = summaryMap.values.mapNotNull { it.value }
        val dynamicCounters = allSummaries.filter { it.type == org.kde.bettercounter.persistence.CounterType.DYNAMIC }
        
        for (counter in dynamicCounters) {
            val oldFormula = counter.formula
            if (oldFormula.isNullOrBlank()) continue

            val newFormula = FormulaEvaluator.replaceOperandInFormula(oldFormula, oldName, newName)
            if (oldFormula != newFormula) {
                val metadata = CounterMetadata(
                    counter.name, counter.interval, counter.goal, counter.color,
                    repo.getCounterCategory(counter.name), counter.type, newFormula
                )
                repo.setCounterMetadata(metadata)
            }
        }
    }

    private fun CoroutineScope.updateFormulasOnDelete(deletedName: String) {
        // This logic is tricky. A simple replacement might not be enough.
        // For now, we'll rely on validation at edit time to force the user to fix broken formulas.
        // A more advanced implementation could invalidate the formula here.
        Log.d(TAG, "Counter '$deletedName' was deleted. Any formulas referencing it are now invalid.")
    }

    // --- Formula Evaluation ---

    sealed class FormulaValidationResult {
        object Valid : FormulaValidationResult()
        data class Invalid(val error: String) : FormulaValidationResult()
    }

    private object FormulaEvaluator {
        // Regex to tokenize the formula into operands (words, sum(), numbers) and operators
        private val tokenizerRegex = "sum\\([^)]+\\)|[\\p{L}0-9_]+|[+\\-*]".toRegex()

        fun evaluate(formula: String, counters: Map<String, Int>, categories: Map<String, Int>): Int {
            if (formula.isBlank()) return 0

            val tokens = tokenizerRegex.findAll(formula).map { it.value }.toMutableList()
            if (tokens.isEmpty()) return 0

            // 1. Resolve values for all operands
            val values = tokens.map { token ->
                when {
                    token.toIntOrNull() != null -> token.toInt()
                    token.startsWith("sum(") && token.endsWith(")") -> {
                        val category = token.substring(4, token.length - 1)
                        categories[category] ?: 0
                    }
                    token in counters -> counters[token] ?: 0
                    // If it's an operator, keep it as a string for now
                    token in listOf("+", "-", "*") -> token
                    // Unknown operands evaluate to 0
                    else -> 0
                }
            }.toMutableList()

            // 2. Perform multiplication pass
            val mulTokens = mutableListOf<Any>()
            var i = 0
            while (i < values.size) {
                val token = values[i]
                if (token == "*" && i > 0 && i < values.size - 1) {
                    val left = mulTokens.removeLast() as Int
                    val right = values[i + 1] as Int
                    mulTokens.add(left * right)
                    i += 2 // Skip operator and right operand
                } else {
                    mulTokens.add(token)
                    i++
                }
            }

            // 3. Perform addition/subtraction pass
            var total = 0
            var currentOp: (Int, Int) -> Int = Int::plus
            if (mulTokens.isNotEmpty()) {
                val firstToken = mulTokens[0]
                if (firstToken is Int) {
                    total = firstToken
                }
            }

            i = 1
            while (i < mulTokens.size) {
                val op = mulTokens[i]
                val right = mulTokens.getOrNull(i + 1) as? Int ?: 0
                when (op) {
                    "+" -> total += right
                    "-" -> total -= right
                }
                i += 2
            }

            return total
        }

        fun validate(
            formula: String,
            currentCounterName: String,
            allCounters: List<String>,
            allCategories: Set<String>,
            dynamicCounters: Map<String, String>
        ): FormulaValidationResult {
            if (formula.isBlank()) return FormulaValidationResult.Invalid("Formula cannot be empty.")

            // Check for invalid characters, now allowing *
            if (formula.contains(Regex("[^\\p{L}0-9_\\s()+\\-*]"))) {
                return FormulaValidationResult.Invalid("Invalid characters in formula.")
            }
             if (formula.trim().matches(Regex(".*[+\\-*]{2,}.*"))) {
                return FormulaValidationResult.Invalid("Consecutive operators are not allowed.")
            }
            if (formula.trim().endsWith("+") || formula.trim().endsWith("-") || formula.trim().endsWith("*")) {
                return FormulaValidationResult.Invalid("Formula cannot end with an operator.")
            }

            val tokens = tokenizerRegex.findAll(formula).map { it.value }.toList()
            if (tokens.isEmpty()) return FormulaValidationResult.Invalid("Formula is empty or invalid.")

            val dependencies = mutableSetOf<String>()

            for (token in tokens) {
                when {
                    // It's a number, it's valid.
                    token.toIntOrNull() != null -> {}
                    // It's an operator, it's valid.
                    token in listOf("+", "-", "*") -> {}
                    
                    token.startsWith("sum(") && token.endsWith(")") -> {
                        val category = token.substring(4, token.length - 1)
                        if (!allCategories.contains(category)) {
                            return FormulaValidationResult.Invalid("Category '$category' does not exist.")
                        }
                    }
                    else -> { // It must be a counter name
                        if (!allCounters.contains(token)) {
                            return FormulaValidationResult.Invalid("Counter or number '$token' is not valid.")
                        }
                        if (token == currentCounterName) {
                            return FormulaValidationResult.Invalid("A counter cannot reference itself.")
                        }
                        dependencies.add(token)
                    }
                }
            }

            // Check for circular dependencies
            val visited = mutableSetOf<String>()
            val path = mutableSetOf<String>()
            for (dep in dependencies) {
                if (hasCircularDependency(dep, dynamicCounters, visited, path)) {
                    return FormulaValidationResult.Invalid("Circular dependency detected involving '$dep'.")
                }
            }

            return FormulaValidationResult.Valid
        }

        private fun hasCircularDependency(
            counterName: String,
            dynamicCounters: Map<String, String>,
            visited: MutableSet<String>,
            path: MutableSet<String>
        ): Boolean {
            if (path.contains(counterName)) return true // Cycle detected
            if (visited.contains(counterName)) return false // Already checked this node

            path.add(counterName)
            
            val formula = dynamicCounters[counterName]
            if (!formula.isNullOrBlank()) {
                val dependencies = getDependenciesFromFormula(formula)
                for (dep in dependencies) {
                    if (hasCircularDependency(dep, dynamicCounters, visited, path)) {
                        return true
                    }
                }
            }

            path.remove(counterName)
            visited.add(counterName)
            return false
        }
        
        fun getDependenciesFromFormula(formula: String): Set<String> {
            val dependencies = mutableSetOf<String>()
            val tokens = tokenizerRegex.findAll(formula).map { it.value }.toList()
            for (token in tokens) {
                if (token.toIntOrNull() == null && token !in listOf("+", "-", "*") && !token.startsWith("sum(")) {
                    dependencies.add(token)
                }
            }
            return dependencies
        }
        
        fun replaceOperandInFormula(formula: String, oldOperand: String, newOperand: String): String {
            // This uses a regex with word boundaries to avoid replacing parts of names
            // E.g. "Task" -> "New Task" should not change "Super Task"
            val regex = "\\b${Regex.escape(oldOperand)}\\b".toRegex()
            return formula.replace(regex, newOperand)
        }
    }

    // --- End Dynamic Counter Logic ---

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
                    if (counterEntries.isEmpty() && summary.type == org.kde.bettercounter.persistence.CounterType.STANDARD) continue
    
                    val colorInt = extractColorInt(summary.color)
                    val colorRGB = intToRGBString(colorInt)
                    val colorName = getClosestColorName(colorInt)
                    val category = repo.getCounterCategory(counterName)
    
                    val type = summary.type.name
                    val formula = summary.formula?.let { "\"$it\"" } ?: "null"
    
                    // Create JSON with all metadata
                    val configJson = """{"name":"$counterName","color":"$colorRGB","colorName":"$colorName","interval":"${summary.interval}","goal":${summary.goal},"category":"$category","type":"$type","formula":$formula,"step":${summary.step}}"""
                    
                    val timestamps = counterEntries.joinToString(",") { it.date.time.toString() }
                    
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
    
    private fun intToRGBString(colorInt: Int): String {
        val red = (colorInt shr 16) and 0xFF
        val green = (colorInt shr 8) and 0xFF
        val blue = colorInt and 0xFF
        return "#%02X%02X%02X".format(red, green, blue) 
    }
    
    private fun getClosestColorName(colorInt: Int): String {
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
        val red = (colorInt shr 16) and 0xFF
        val green = (colorInt shr 8) and 0xFF
        val blue = colorInt and 0xFF
        var closestColor = "CUSTOM"
        var minDistance = Int.MAX_VALUE
        
        for ((stdColorInt, name) in colorMap) {
            val stdRed = (stdColorInt shr 16) and 0xFF
            val stdGreen = (stdColorInt shr 8) and 0xFF
            val stdBlue = stdColorInt and 0xFF
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
    
    private fun extractColorInt(color: CounterColor): Int {
        val colorStr = color.toString()
        return try {
            val startIndex = colorStr.indexOf("colorInt=") + 9
            val endIndex = colorStr.indexOf(")", startIndex)
            if (startIndex > 9 && endIndex > startIndex) {
                colorStr.substring(startIndex, endIndex).toInt()
            } else { 0 }
        } catch (e: Exception) {
            Log.e(TAG, "无法提取颜色值: $colorStr", e)
            0
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
                    
                    // Process counters sequentially to avoid race conditions
                    namesToImport.forEach { name ->
                        val metadata = metadataToUpdate[name]
                        if (metadata != null) {
                            // Overwrite existing counters
                            if (counterExists(name)) {
                                // Perform deletion logic synchronously
                                repo.removeAllEntries(name)
                                repo.deleteCounterMetadata(name)
                                val list = repo.getCounterList().toMutableList()
                                list.remove(name)
                                repo.setCounterList(list)
                                synchronized(this) {
                                    summaryMap.remove(name)
                                }
                            }
                            // Add the counter with new metadata
                            repo.setCounterList(repo.getCounterList().toMutableList() + name)
                            repo.setCounterMetadata(metadata)

                        } else if (!counterExists(name)) {
                            // Handle old format import for new counters
                            val defaultMetadata = CounterMetadata(
                                name, 
                                Interval.DEFAULT, 
                                0, 
                                CounterColor.getDefault(context),
                                "默认",
                                org.kde.bettercounter.persistence.CounterType.STANDARD,
                                null
                            )
                            repo.setCounterList(repo.getCounterList().toMutableList() + name)
                            repo.setCounterMetadata(defaultMetadata)
                        }
                    }
                    
                    // Bulk insert all entries from the file
                    repo.bulkAddEntries(entriesToImport)
                    Log.d(TAG, "[导入] 实际导入条目总数: ${entriesToImport.size}")
                    
                    // Refresh summaries for all imported counters
                    namesToImport.forEach { name ->
                        val summary = repo.getCounterSummary(name)
                        synchronized(this) {
                            if (summaryMap[name] == null) {
                                summaryMap[name] = MutableLiveData()
                            }
                            summaryMap[name]?.postValue(summary)
                        }
                    }
                    
                    recalculateDynamicCounters()
                    
                    withContext(Dispatchers.Main) {
                        refreshLiveData()
                    }
                    
                    sendProgress(namesToImport.size, 1)
                } catch (e: Exception) {
                    Log.e(TAG, "[导入] Import failed: ${e.javaClass.simpleName}: ${e.message}")
                    sendProgress(namesToImport.size, -1)
                }
            }
        }
    }
    
    internal fun parseImportLineWithJSON(
        line: String,
        namesToImport: MutableList<String>,
        entriesToImport: MutableList<Entry>,
        metadataToUpdate: MutableMap<String, CounterMetadata>,
        context: Context? = null
    ) {
        if (line.isEmpty()) return
        
        try {
            if (line.startsWith("{")) {
                var jsonEnd = line.indexOf("},[")
                var timestampsPart: String
                var jsonPart: String
                
                if (jsonEnd <= 0) {
                    jsonEnd = line.indexOf("}, [")
                    if (jsonEnd <= 0) {
                        Log.e(TAG, "[导入] JSON格式错误：找不到时间戳数组分隔符 '},[' 或 '}, ['，行: $line")
                        return
                    }
                    jsonPart = line.substring(0, jsonEnd + 1)
                    timestampsPart = line.substring(jsonEnd + 4).trim()
                } else {
                    jsonPart = line.substring(0, jsonEnd + 1)
                    timestampsPart = line.substring(jsonEnd + 3)
                }
                
                val configMap = parseJsonObject(jsonPart)
                val name = configMap["name"] as? String
                if (name.isNullOrEmpty()) {
                    Log.e(TAG, "[导入] JSON格式错误：缺少name字段或name为空，行: $line")
                    return
                }
                
                val colorValue = configMap["color"] ?: configMap["colorName"]
                val colorInt = getColorIntFromValue(colorValue)
                val safeContext = context ?: return
                val color = createCounterColorFromInt(colorInt, safeContext)
                
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
                val category = (configMap["category"] as? String)?.takeIf { it.isNotBlank() } ?: "默认"
                
                val typeStr = configMap["type"] as? String
                val type = if (typeStr == "DYNAMIC") org.kde.bettercounter.persistence.CounterType.DYNAMIC else org.kde.bettercounter.persistence.CounterType.STANDARD
                val formula = configMap["formula"] as? String
                val step = (configMap["step"] as? Number)?.toInt() ?: 1

                val metadata = CounterMetadata(name, interval, goal, color, category, type, formula, step)
                metadataToUpdate[name] = metadata
                
                parseTimestamps(timestampsPart, name, entriesToImport)
                
                if (!namesToImport.contains(name)) {
                    namesToImport.add(name)
                }
            } else {
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
            if (cleaned.startsWith("[")) {
                cleaned = cleaned.removePrefix("[")
            }
            if (cleaned.endsWith("]")) {
                cleaned = cleaned.removeSuffix("]")
            }
            cleaned = cleaned.trim()
            
            if (cleaned.isEmpty()) {
                Log.d(TAG, "[导入] 计数器 $counterName 没有时间戳条目")
                return
            }
            
            val timestamps = cleaned.split(",")
            for (timestamp in timestamps) {
                try {
                    entries.add(Entry(name = counterName, date = Date(timestamp.trim().toLong())))
                } catch (e: Exception) {
                     Log.w(TAG, "[导入] 无法解析时间戳: ${timestamp}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[导入] 解析时间戳数组失败: $timestampsStr", e)
        }
    }
    
    private fun CounterColor.Companion.getDefaultInt(): Int {
        return 0xFF2196F3.toInt() // 默认蓝色
    }

    fun getEntriesForRangeSortedByDate(name: String, since: Date, until: Date): LiveData<List<Entry>> {
        val ret = MutableLiveData<List<Entry>>()
        CoroutineScope(Dispatchers.IO).launch {
            val entries = repo.getEntriesForRangeSortedByDate(name, since, until)
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
        val summaries = mutableListOf<Pair<String, CounterSummary>>()
        for ((name, _) in entries) {
            val counterSummary = repo.getCounterSummary(name) // IO线程
            summaries.add(Pair(name, counterSummary))
        }
        withContext(Dispatchers.Main) {
            synchronized(this) {
                for ((name, counterSummary) in summaries) {
                    summaryMap[name]?.value = counterSummary
                }
            }
        }
        recalculateDynamicCounters()
    }

    companion object {
        fun parseImportLine(line: String, namesToImport: MutableList<String>, entriesToImport: MutableList<Entry>) {
            val nameAndDates = line.splitToSequence(",").iterator()
            var name = nameAndDates.next()
            var nameEnded = false
            nameAndDates.forEach { timestamp ->
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
    private fun getColorForName(colorName: String, context: Context): CounterColor {
        val defaultColor = CounterColor.getDefault(context)
        return when (colorName.uppercase()) {
            "BLACK" -> CounterColor.getDefault(context)
            "RED" -> CounterColor.getDefault(context) 
            "GREEN" -> CounterColor.getDefault(context) 
            "BLUE" -> CounterColor.getDefault(context) 
            "YELLOW" -> CounterColor.getDefault(context) 
            "PURPLE" -> CounterColor.getDefault(context) 
            "ORANGE" -> CounterColor.getDefault(context) 
            "CYAN" -> CounterColor.getDefault(context) 
            "PINK" -> CounterColor.getDefault(context) 
            "GRAY" -> CounterColor.getDefault(context) 
            else -> defaultColor
        }
    }
    
    private fun getColorName(color: CounterColor): String {
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
            else -> "DEFAULT"
        }
    }
    
    private fun mapColorValueToName(colorValue: Int): String {
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
    
    private fun createCounterColorFromInt(colorInt: Int, context: Context): CounterColor {
        val defaultColor = CounterColor.getDefault(context)
        
        return try {
            val constructor = CounterColor::class.java.getDeclaredConstructor(Int::class.java)
            constructor.isAccessible = true
            constructor.newInstance(colorInt)
        } catch (e: Exception) {
            try {
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
    
    private fun getColorIntForName(colorName: String): Int {
        return when (colorName.uppercase()) {
            "BLACK" -> -16777216 
            "RED" -> -65536 
            "GREEN" -> -16711936 
            "BLUE" -> -16776961 
            "YELLOW" -> -256 
            "PURPLE" -> -8388480 
            "ORANGE" -> -23296 
            "CYAN" -> -16711681 
            "PINK" -> -65281 
            "GRAY" -> -7829368 
            else -> 0
        }
    }
    
    private fun getColorIntFromValue(colorValue: Any?): Int {
        return when (colorValue) {
            is Number -> colorValue.toInt()
            is String -> {
                val colorStr = colorValue.toString()
                when {
                    colorStr.startsWith("#") && (colorStr.length == 7 || colorStr.length == 9) -> {
                        try {
                            Color.parseColor(colorStr)
                        } catch (e: Exception) {
                            getColorIntForName("DEFAULT")
                        }
                    }
                    colorStr.matches(Regex("-?\\d+")) -> {
                        try {
                            colorStr.toInt()
                        } catch (e: Exception) {
                            getColorIntForName("DEFAULT")
                        }
                    }
                    else -> {
                        getColorIntForName(colorStr)
                    }
                }
            }
            else -> getColorIntForName("DEFAULT")
        }
    }
    
    suspend fun getAllEntriesSortedByDate(name: String): List<Entry> {
        return repo.getAllEntriesSortedByDate(name)
    }
    
    suspend fun hasDataToExport(): Boolean {
        val counters = repo.getCounterList()
        for (name in counters) {
            if (repo.getAllEntriesSortedByDate(name).isNotEmpty()) {
                return true
            }
        }
        return false
    }
    
        private fun refreshLiveData() {
                        CoroutineScope(Dispatchers.IO).launch {
                            val counters = repo.getCounterList()
                            val summaries = mutableListOf<Pair<String, CounterSummary>>() // Changed to hold CounterSummary directly
                            for (name in counters) {
                                val summary = repo.getCounterSummary(name)
                                summaries.add(Pair(name, summary))
                            }
            
                            withContext(Dispatchers.Main) {
                                synchronized(this) {
                                    for ((name, summary) in summaries) {
                                        summaryMap[name]?.value = summary
                                    }
                                    val observersCopy = counterObservers.toList()
                                    for (observer in observersCopy) {
                                        observer.onInitialCountersLoaded()
                                    }
                                }
                            }
                            // Explicitly call the extension function on the current CoroutineScope
                            recalculateDynamicCounters()
                        }
                    }
}
