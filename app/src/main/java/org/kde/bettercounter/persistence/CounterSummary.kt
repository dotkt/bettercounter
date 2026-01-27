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

    var category: String,

    var lastIntervalCount: Int,

    var totalCount: Int,

    var leastRecent: Date?,

    var mostRecent: Date?,

    var type: CounterType = CounterType.STANDARD,

    var formula: String? = null,

    var step: Int = 1

) {

    fun latestBetweenNowAndMostRecentEntry(): Date {

        val now = Calendar.getInstance().time

        val lastEntry = mostRecent
        return if (lastEntry != null && lastEntry > now) lastEntry else now
    }

    fun isGoalMet(): Boolean {
        return goal in 1..lastIntervalCount
    }

    fun getFormattedCount(forWidget: Boolean = false): String {
        if (interval == Interval.MYTIMER) {
            if (lastIntervalCount % 2 == 0) {
                return "▶"
            } else {
                return "■"
            }
        } else {
            if (forWidget) {
                if (goal > 0) {
                    if (lastIntervalCount < goal) {
                        return "$lastIntervalCount/$goal"
                    } else {
                        return "\uD83D\uDC4D"
                    }
                } else {
                    return lastIntervalCount.toString()
                }
            } else {
                if (goal > 0) {
                    return "$lastIntervalCount/$goal"
                } else {
                    return lastIntervalCount.toString()
                }
            }
        }
    }

}
