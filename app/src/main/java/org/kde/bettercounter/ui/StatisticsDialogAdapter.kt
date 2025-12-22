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
            android.util.Log.d("StatisticsAdapter", "更新月统计表格，年份=$year, 条目数=${entries.size}")
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

            // 创建月份和日期到是否有记录的映射
            // Key: Pair<月份(0-11), 日期(1-31)>，Value: 是否有记录
            val monthDayMap = mutableMapOf<Pair<Int, Int>, Boolean>()
            
            // 遍历该年的每一天，建立月份和日期到是否有记录的映射
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, Calendar.JANUARY)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            // 判断该年有多少天（考虑闰年）
            val isLeapYear = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
            val daysInYear = if (isLeapYear) 366 else 365
            
            // 遍历该年的每一天
            for (dayOffset in 0 until daysInYear) {
                val currentYear = calendar.get(Calendar.YEAR)
                val currentMonth = calendar.get(Calendar.MONTH) // 0-11
                val currentDay = calendar.get(Calendar.DAY_OF_MONTH) // 1-31
                
                // 只处理该年的日期
                if (currentYear == year) {
                    // 检查这一天是否有记录
                    val hasEntry = entryDates.contains(Triple(currentYear, currentMonth, currentDay))
                    
                    // 存储到映射中
                    monthDayMap[Pair(currentMonth, currentDay)] = hasEntry
                } else {
                    break
                }
                
                // 移动到下一天
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            
            android.util.Log.d("StatisticsAdapter", "映射条目数=${monthDayMap.size}")
            
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

            // 从1月到12月，生成每一行（月）
            for (month in Calendar.JANUARY..Calendar.DECEMBER) {
                val row = TableRow(view.context)
                val rowParams = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )
                rowParams.setMargins(0, 0, 0, 0)
                row.layoutParams = rowParams

                // 月份号（1-12），10月显示为"0"，11月显示为"1"，12月显示为"2"
                val monthCell = TextView(view.context)
                val monthNumber = month + 1 // Calendar.MONTH从0开始，显示时+1
                monthCell.text = if (monthNumber >= 10) {
                    (monthNumber - 10).toString() // 10月显示"0"，11月显示"1"，12月显示"2"
                } else {
                    monthNumber.toString()
                }
                monthCell.textSize = 9f
                monthCell.setTextColor(android.graphics.Color.BLACK)
                monthCell.gravity = android.view.Gravity.CENTER
                monthCell.setPadding(2, 1, 2, 1)
                val monthCellParams = TableRow.LayoutParams(20, TableRow.LayoutParams.WRAP_CONTENT)
                monthCellParams.setMargins(0, 0, 0, 0)
                monthCell.layoutParams = monthCellParams
                row.addView(monthCell)

                // 获取该月的天数
                val monthCalendar = Calendar.getInstance()
                monthCalendar.set(Calendar.YEAR, year)
                monthCalendar.set(Calendar.MONTH, month)
                monthCalendar.set(Calendar.DAY_OF_MONTH, 1)
                val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

                // 1-31号
                for (day in 1..31) {
                    val dayCell = TextView(view.context)
                    dayCell.textSize = 8.5f // 12f * 0.95 = 11.4f
                    dayCell.gravity = android.view.Gravity.CENTER
                    dayCell.setPadding(1, 1, 1, 1)
                    val dayCellParams = TableRow.LayoutParams(25, TableRow.LayoutParams.WRAP_CONTENT)
                    dayCellParams.setMargins(0, 0, 0, 0)
                    dayCell.layoutParams = dayCellParams

                    if (day > daysInMonth) {
                        // 该月没有这一天，显示空白
                        dayCell.text = ""
                        dayCell.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    } else {
                        // 计算这个单元格对应的实际日期
                        val cellDate = Calendar.getInstance()
                        cellDate.set(Calendar.YEAR, year)
                        cellDate.set(Calendar.MONTH, month)
                        cellDate.set(Calendar.DAY_OF_MONTH, day)
                        cellDate.set(Calendar.HOUR_OF_DAY, 0)
                        cellDate.set(Calendar.MINUTE, 0)
                        cellDate.set(Calendar.SECOND, 0)
                        cellDate.set(Calendar.MILLISECOND, 0)
                        
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
                            val hasEntry = monthDayMap[Pair(month, day)] ?: false
                            
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

