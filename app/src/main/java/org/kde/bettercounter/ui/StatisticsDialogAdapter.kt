package org.kde.bettercounter.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import org.kde.bettercounter.R
import org.kde.bettercounter.persistence.Entry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StatisticsDialogAdapter(
    private val entries: List<Entry>,
    private val counterName: String,
    private val viewPager: ViewPager2
) : RecyclerView.Adapter<StatisticsDialogAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = when (viewType) {
            0 -> LayoutInflater.from(parent.context).inflate(R.layout.fragment_week_statistics, parent, false)
            1 -> LayoutInflater.from(parent.context).inflate(R.layout.fragment_text_statistics, parent, false)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
        return ViewHolder(view)
    }

    override fun getItemViewType(position: Int): Int = position

    override fun getItemCount(): Int = 2

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (position) {
            0 -> bindWeekStatistics(holder.view)
            1 -> bindTextStatistics(holder.view)
        }
    }

    private fun bindWeekStatistics(view: View) {
        val tableLayout = view.findViewById<TableLayout>(R.id.tableLayout)
        val tvYear = view.findViewById<TextView>(R.id.tvYear)
        val btnPrevYear = view.findViewById<android.widget.Button>(R.id.btnPrevYear)
        val btnNextYear = view.findViewById<android.widget.Button>(R.id.btnNextYear)

        var currentYear = Calendar.getInstance().get(Calendar.YEAR)

        fun updateWeekTable(year: Int) {
            android.util.Log.d("StatisticsAdapter", "更新周统计表格，年份=$year, 条目数=${entries.size}")
            tvYear.text = year.toString()
            tableLayout.removeViews(1, tableLayout.childCount - 1) // 保留表头

            // 创建日期到条目的映射（使用年月日作为key，忽略时间部分）
            val entryDates = entries.map { entry ->
                val cal = Calendar.getInstance()
                cal.time = entry.date
                // 重置时间部分为0，只比较日期
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                // 使用年月日作为唯一标识
                Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            }.toSet()
            
            android.util.Log.d("StatisticsAdapter", "创建了 ${entryDates.size} 个日期映射")

            // 创建周数和星期几到是否有记录的映射
            // Key: Pair<周数, 星期几>，Value: 是否有记录
            val weekDayMap = mutableMapOf<Pair<Int, Int>, Boolean>()
            
            // 设置Calendar为ISO 8601标准（周从周一开始，第一周至少包含4天）
            val calendar = Calendar.getInstance()
            calendar.firstDayOfWeek = Calendar.MONDAY
            calendar.setMinimalDaysInFirstWeek(4)
            
            // 设置到该年的第一天
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, Calendar.JANUARY)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            // 获取第一天的周数
            var firstWeek = calendar.get(Calendar.WEEK_OF_YEAR)
            android.util.Log.d("StatisticsAdapter", "1月1日: 年份=${calendar.get(Calendar.YEAR)}, 周数=$firstWeek, 星期几=${calendar.get(Calendar.DAY_OF_WEEK)}")
            
            // 如果1月1日的周数很大（52或53），说明它属于上一年的最后一周
            // 需要找到该年真正的第一周（第一个完整的周）
            if (firstWeek >= 52) {
                // 找到该年第一个周一的周数
                val firstMonday = calendar.clone() as Calendar
                while (firstMonday.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    firstMonday.add(Calendar.DAY_OF_MONTH, 1)
                }
                firstWeek = firstMonday.get(Calendar.WEEK_OF_YEAR)
                android.util.Log.d("StatisticsAdapter", "调整后第一周=$firstWeek")
            }
            
            // 判断该年有多少天（考虑闰年）
            val isLeapYear = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
            val daysInYear = if (isLeapYear) 366 else 365
            
            var processedDays = 0
            var minWeek = Int.MAX_VALUE
            var maxWeek = Int.MIN_VALUE
            
            // 重置calendar到1月1日
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, Calendar.JANUARY)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            // 遍历该年的每一天
            for (dayOffset in 0 until daysInYear) {
                // 获取当前日期
                val currentYear = calendar.get(Calendar.YEAR)
                val currentMonth = calendar.get(Calendar.MONTH)
                val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
                
                // 只处理该年的日期
                if (currentYear == year) {
                    // 获取周数和星期几
                    var week = calendar.get(Calendar.WEEK_OF_YEAR)
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    
                    // 如果周数很大（52或53），且是1月，说明这是上一年的周
                    // 需要找到该年真正的周数
                    if (week >= 52 && currentMonth == Calendar.JANUARY) {
                        // 找到该周的第一个周一，看它属于哪一年
                        val weekMonday = calendar.clone() as Calendar
                        while (weekMonday.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                            weekMonday.add(Calendar.DAY_OF_MONTH, -1)
                        }
                        if (weekMonday.get(Calendar.YEAR) < year) {
                            // 这是上一年的周，需要找到该年第一个完整的周
                            val firstMonday = calendar.clone() as Calendar
                            while (firstMonday.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || firstMonday.get(Calendar.YEAR) != year) {
                                firstMonday.add(Calendar.DAY_OF_MONTH, 1)
                            }
                            week = firstMonday.get(Calendar.WEEK_OF_YEAR)
                        }
                    }
                    
                    // 更新最小和最大周数
                    if (week < minWeek) minWeek = week
                    if (week > maxWeek) maxWeek = week
                    
                    // 检查这一天是否有记录
                    val hasEntry = entryDates.contains(Triple(currentYear, currentMonth, currentDay))
                    
                    // 存储到映射中
                    weekDayMap[Pair(week, dayOfWeek)] = hasEntry
                    processedDays++
                    
                    // 调试前几天的数据
                    if (dayOffset < 5) {
                        android.util.Log.d("StatisticsAdapter", "处理: $currentYear-$currentMonth-$currentDay, 周数=$week, 星期几=$dayOfWeek, 有记录=$hasEntry")
                    }
                } else {
                    // 已经跨年了，停止处理
                    android.util.Log.d("StatisticsAdapter", "跨年停止: $currentYear-$currentMonth-$currentDay")
                    break
                }
                
                // 移动到下一天
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            
            android.util.Log.d("StatisticsAdapter", "处理了 $processedDays 天, 最小周=$minWeek, 最大周=$maxWeek")
            
            // 使用遍历过程中找到的最大周数作为最后一周
            val actualLastWeek = if (maxWeek != Int.MIN_VALUE) maxWeek else {
                // 如果遍历失败，使用12月31日的周数作为后备
                val endCalendar = Calendar.getInstance()
                endCalendar.firstDayOfWeek = Calendar.MONDAY
                endCalendar.setMinimalDaysInFirstWeek(4)
                endCalendar.set(Calendar.YEAR, year)
                endCalendar.set(Calendar.MONTH, Calendar.DECEMBER)
                endCalendar.set(Calendar.DAY_OF_MONTH, 31)
                endCalendar.set(Calendar.HOUR_OF_DAY, 0)
                endCalendar.set(Calendar.MINUTE, 0)
                endCalendar.set(Calendar.SECOND, 0)
                endCalendar.set(Calendar.MILLISECOND, 0)
                
                if (endCalendar.get(Calendar.YEAR) > year) {
                    // 往前找到该年的最后一天
                    while (endCalendar.get(Calendar.YEAR) > year) {
                        endCalendar.add(Calendar.DAY_OF_MONTH, -1)
                    }
                    // 找到这一周的周一，获取周数
                    while (endCalendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                        endCalendar.add(Calendar.DAY_OF_MONTH, -1)
                    }
                    endCalendar.get(Calendar.WEEK_OF_YEAR)
                } else {
                    endCalendar.get(Calendar.WEEK_OF_YEAR)
                }
            }
            
            android.util.Log.d("StatisticsAdapter", "第一周=$firstWeek, 最后一周=$actualLastWeek, 映射条目数=${weekDayMap.size}")
            
            // 调试：检查前几周的映射数据
            for (testWeek in firstWeek..minOf(firstWeek + 2, actualLastWeek)) {
                for (testDay in listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY)) {
                    val testKey = Pair(testWeek, testDay)
                    val testValue = weekDayMap[testKey]
                    android.util.Log.d("StatisticsAdapter", "调试: 周$testWeek 星期$testDay -> $testValue")
                }
            }

            // 从第一周开始，生成每一行（周）
            for (week in firstWeek..actualLastWeek) {
                val row = TableRow(view.context)
                row.layoutParams = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )

                // 周号
                val weekCell = TextView(view.context)
                weekCell.text = week.toString()
                weekCell.textSize = 11f
                weekCell.setTextColor(android.graphics.Color.BLACK)
                weekCell.gravity = android.view.Gravity.CENTER
                weekCell.setPadding(8, 8, 8, 8)
                weekCell.layoutParams = TableRow.LayoutParams(60, TableRow.LayoutParams.WRAP_CONTENT)
                row.addView(weekCell)

                // 星期一到星期日
                // Calendar.MONDAY=2, Calendar.TUESDAY=3, ..., Calendar.SATURDAY=7, Calendar.SUNDAY=1
                val daysOfWeek = listOf(
                    Calendar.MONDAY,    // 2
                    Calendar.TUESDAY,   // 3
                    Calendar.WEDNESDAY, // 4
                    Calendar.THURSDAY,  // 5
                    Calendar.FRIDAY,    // 6
                    Calendar.SATURDAY,  // 7
                    Calendar.SUNDAY     // 1
                )
                
                for (dayOfWeek in daysOfWeek) {
                    val dayCell = TextView(view.context)
                    dayCell.textSize = 16f
                    dayCell.gravity = android.view.Gravity.CENTER
                    dayCell.setPadding(4, 4, 4, 4)
                    dayCell.layoutParams = TableRow.LayoutParams(40, TableRow.LayoutParams.WRAP_CONTENT)

                    // 从映射中查找
                    val hasEntry = weekDayMap[Pair(week, dayOfWeek)] ?: false

                    if (hasEntry) {
                        dayCell.text = "✅"
                        dayCell.setTextColor(android.graphics.Color.parseColor("#4CAF50")) // 绿色
                    } else {
                        dayCell.text = "❌"
                        dayCell.setTextColor(android.graphics.Color.parseColor("#F44336")) // 红色
                    }

                    row.addView(dayCell)
                }

                tableLayout.addView(row)
            }
        }

        btnPrevYear.setOnClickListener {
            currentYear--
            updateWeekTable(currentYear)
        }

        btnNextYear.setOnClickListener {
            currentYear++
            updateWeekTable(currentYear)
        }

        updateWeekTable(currentYear)
    }

    private fun bindTextStatistics(view: View) {
        val textView = view.findViewById<TextView>(R.id.tvTextStats)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val statsText = StringBuilder()
        statsText.append("计数器: $counterName\n\n")
        statsText.append("总计数: ${entries.size}\n")
        statsText.append("区间类型: ${getIntervalType()}\n\n")

        if (entries.isNotEmpty()) {
            statsText.append("最早记录: ${sdf.format(entries.first().date)}\n")
            statsText.append("最新记录: ${sdf.format(entries.last().date)}\n\n")

            if (entries.size > 1) {
                val totalDays = (entries.last().date.time - entries.first().date.time) / (1000.0 * 60 * 60 * 24)
                val avgDays = totalDays / (entries.size - 1)
                val avgFrequency = if (totalDays > 0) (entries.size - 1) / totalDays else 0.0
                statsText.append("总天数: ${String.format("%.2f", totalDays)} 天\n")
                statsText.append("平均间隔: ${String.format("%.2f", avgDays)} 天\n")
                statsText.append("平均频率: ${String.format("%.2f", avgFrequency)} 次/天\n\n")
            }

            statsText.append("--- 最近10条记录 ---\n")
            entries.takeLast(10).forEachIndexed { index, entry ->
                statsText.append("${index + 1}. ${sdf.format(entry.date)}\n")
            }
        } else {
            statsText.append("暂无记录")
        }

        textView.text = statsText.toString()
    }

    private fun getIntervalType(): String {
        // 这里可以根据需要返回区间类型，暂时返回固定值
        return "LIFETIME"
    }
}

