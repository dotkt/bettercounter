package org.kde.bettercounter.persistence

import java.util.Calendar
import java.util.Date

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
