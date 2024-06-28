package org.kde.bettercounter.persistence

import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.*

class CounterSummary(
    var name: String,
    var interval: Interval,
    var goal: Int,
    var color: CounterColor,
    var lastIntervalCount: Int,
    var totalCount: Int,
    var leastRecent: Date?,
    var mostRecent: Date?,
) {
    fun latestBetweenNowAndMostRecentEntry(): Date {
        val now = Calendar.getInstance().time
        val lastEntry = mostRecent
        return if (lastEntry != null && lastEntry > now) lastEntry else now
    }

    fun isGoalMet(): Boolean {
        return goal in 1..lastIntervalCount
    }

    fun getFormattedCount(): CharSequence = buildString {
        if (interval == Interval.MYTIMER) {
            if (totalCount % 2 == 1) {
                // totalCount 是奇数，显示距离 mostRecent 过去的分和秒，格式为 00:00
                //val diffMillis = System.currentTimeMillis() - mostRecent.time
                val diffMillis = mostRecent?.let {
                    System.currentTimeMillis() - it.time
                } ?: 0L

                val minutes = (diffMillis / (1000 * 60)) % 60
                val seconds = (diffMillis / 1000) % 60
                val hours = (diffMillis / (1000 * 60 * 60)) % 24

                val formattedTime = when {
                    hours > 0 -> String.format(Locale.getDefault(), "%02d.%d", hours, minutes / 10)
                    minutes > 10 -> String.format(Locale.getDefault(), "%02d.%d", minutes, seconds / 10)
                    else -> String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
                }

                append(formattedTime)

            } else {
                // totalCount 是偶数，显示 start 字符串
                append("▶")
            }
        } else {
            // 如果不是 MYTIMER 类型，保持原有逻辑
            if (goal > 0) {
                if (lastIntervalCount < goal) {
                    append(lastIntervalCount)
                    append('/')
                    append(goal)
                } else {
                    append("\uD83D\uDC4D")
                }
            } else {
                append(lastIntervalCount)
            }
        }
    }

}
