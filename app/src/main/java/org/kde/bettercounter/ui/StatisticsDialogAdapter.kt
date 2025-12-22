package org.kde.bettercounter.ui

import android.view.View
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import org.kde.bettercounter.R
import org.kde.bettercounter.persistence.Entry
import java.util.Calendar

class StatisticsDialogAdapter(
    private val entries: List<Entry>,
    private val counterName: String,
    private val viewPager: androidx.viewpager2.widget.ViewPager2?
) {

    // 公共方法：直接绑定周统计视图
    fun bindWeekStatisticsView(view: View) {
        bindWeekStatistics(view)
    }

    private fun bindWeekStatistics(view: View) {
        val tableLayout = view.findViewById<TableLayout>(R.id.tableLayout)
        
        // 获取当前年份
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        fun updateWeekTable(year: Int) {
            android.util.Log.d("StatisticsAdapter", "更新周统计表格，年份=$year, 条目数=${entries.size}")
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
            
            // 获取今天的日期（只比较年月日，忽略时间）
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)
            val todayYear = today.get(Calendar.YEAR)
            val todayMonth = today.get(Calendar.MONTH)
            val todayDay = today.get(Calendar.DAY_OF_MONTH)
            
            // 找到最早的数据点日期
            val earliestDate = if (entries.isNotEmpty()) {
                val earliest = Calendar.getInstance()
                earliest.time = entries.minByOrNull { it.date.time }?.date ?: entries.first().date
                earliest.set(Calendar.HOUR_OF_DAY, 0)
                earliest.set(Calendar.MINUTE, 0)
                earliest.set(Calendar.SECOND, 0)
                earliest.set(Calendar.MILLISECOND, 0)
                earliest
            } else {
                null
            }

            // 从第一周开始，生成每一行（周）
            for (week in firstWeek..actualLastWeek) {
                val row = TableRow(view.context)
                val rowParams = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )
                rowParams.setMargins(0, 0, 0, 0) // 移除行距
                row.layoutParams = rowParams

                // 计算这一周的周一
                val weekCalendar = Calendar.getInstance()
                weekCalendar.firstDayOfWeek = Calendar.MONDAY
                weekCalendar.setMinimalDaysInFirstWeek(4)
                weekCalendar.set(Calendar.YEAR, year)
                weekCalendar.set(Calendar.WEEK_OF_YEAR, week)
                weekCalendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                weekCalendar.set(Calendar.HOUR_OF_DAY, 0)
                weekCalendar.set(Calendar.MINUTE, 0)
                weekCalendar.set(Calendar.SECOND, 0)
                weekCalendar.set(Calendar.MILLISECOND, 0)
                
                // 获取周一的日期（可能跨年）
                val mondayYear = weekCalendar.get(Calendar.YEAR)
                val mondayMonth = weekCalendar.get(Calendar.MONTH) + 1 // Calendar.MONTH从0开始
                val mondayDay = weekCalendar.get(Calendar.DAY_OF_MONTH)
                
                // 格式化周号：周数.年-月-日
                val weekText = String.format("%02d.%04d-%02d-%02d", week, mondayYear, mondayMonth, mondayDay)
                
                // 周号
                val weekCell = TextView(view.context)
                weekCell.text = weekText
                weekCell.textSize = 9f
                weekCell.setTextColor(android.graphics.Color.BLACK)
                weekCell.gravity = android.view.Gravity.CENTER
                weekCell.setPadding(4, 1, 4, 1) // 减小上下padding
                val weekCellParams = TableRow.LayoutParams(100, TableRow.LayoutParams.WRAP_CONTENT)
                weekCellParams.setMargins(0, 0, 0, 0) // 移除边距
                weekCell.layoutParams = weekCellParams
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
                    dayCell.textSize = 14f
                    dayCell.gravity = android.view.Gravity.CENTER
                    dayCell.setPadding(2, 1, 2, 1) // 减小上下padding
                    val dayCellParams = TableRow.LayoutParams(35, TableRow.LayoutParams.WRAP_CONTENT)
                    dayCellParams.setMargins(0, 0, 0, 0) // 移除边距
                    dayCell.layoutParams = dayCellParams

                    // 计算这个单元格对应的实际日期
                    val cellDate = weekCalendar.clone() as Calendar
                    // 计算从周一到当前星期几的天数差
                    val dayOffset = when (dayOfWeek) {
                        Calendar.MONDAY -> 0
                        Calendar.TUESDAY -> 1
                        Calendar.WEDNESDAY -> 2
                        Calendar.THURSDAY -> 3
                        Calendar.FRIDAY -> 4
                        Calendar.SATURDAY -> 5
                        Calendar.SUNDAY -> 6
                        else -> 0
                    }
                    cellDate.add(Calendar.DAY_OF_MONTH, dayOffset)
                    
                    val cellYear = cellDate.get(Calendar.YEAR)
                    val cellMonth = cellDate.get(Calendar.MONTH)
                    val cellDay = cellDate.get(Calendar.DAY_OF_MONTH)
                    
                    // 判断日期类型
                    val isThisYear = cellYear == year
                    val isToday = cellYear == todayYear && cellMonth == todayMonth && cellDay == todayDay
                    val isFuture = cellDate.after(today)
                    val isBeforeEarliest = earliestDate != null && cellDate.before(earliestDate)
                    
                    if (!isThisYear || isFuture) {
                        // 非今年或未来日期，显示空白
                        dayCell.text = ""
                        dayCell.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    } else {
                        // 从映射中查找
                        val hasEntry = weekDayMap[Pair(week, dayOfWeek)] ?: false
                        
                        if (hasEntry) {
                            dayCell.text = "✅"
                            dayCell.setTextColor(android.graphics.Color.parseColor("#4CAF50")) // 绿色
                        } else if (isToday) {
                            // 今天是今天且没有记录，显示❓
                            dayCell.text = "❓"
                            dayCell.setTextColor(android.graphics.Color.parseColor("#FF9800")) // 橙色
                        } else if (isBeforeEarliest) {
                            // 早于最早数据点且没有记录，显示空白
                            dayCell.text = ""
                            dayCell.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        } else {
                            // 等于或晚于最早数据点且没有记录，显示❌
                            dayCell.text = "❌"
                            dayCell.setTextColor(android.graphics.Color.parseColor("#F44336")) // 红色
                        }
                    }

                    row.addView(dayCell)
                }

                tableLayout.addView(row)
            }
        }

        // 直接显示当前年份的周统计
        updateWeekTable(currentYear)
    }
}

