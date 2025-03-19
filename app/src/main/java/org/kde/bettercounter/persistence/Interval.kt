package org.kde.bettercounter.persistence

import android.content.Context
import org.kde.bettercounter.R
import java.time.temporal.ChronoUnit

enum class Interval(val humanReadableResource: Int) {
    HOUR(R.string.interval_hour),
    DAY(R.string.interval_day),
    WEEK(R.string.interval_week),
    MONTH(R.string.interval_month),
    YEAR(R.string.interval_year),
    MYTIMER(R.string.interval_timer),
    LIFETIME(R.string.interval_lifetime);

    companion object {
        val DEFAULT = DAY

        fun humanReadableValues(context: Context): List<String> {
            return entries.map { context.getString(it.humanReadableResource) }
        }
    }

    fun toChronoUnit(): ChronoUnit {
        return when (this) {
            HOUR -> ChronoUnit.HOURS
            DAY -> ChronoUnit.DAYS
            WEEK -> ChronoUnit.WEEKS
            MONTH -> ChronoUnit.MONTHS
            YEAR -> ChronoUnit.YEARS
            MYTIMER -> throw UnsupportedOperationException("MYTIMER can't be converted to ChronoUnit")
            LIFETIME -> ChronoUnit.FOREVER
        }
    }

    fun toHumanReadableResourceId(): Int {
        return humanReadableResource
    }

    fun toChartDisplayableInterval(): Interval {
        return when (this) {
            LIFETIME -> YEAR  // 对于LIFETIME类型，显示年度统计
            MYTIMER -> DAY    // 对于MYTIMER类型，显示日统计
            else -> this      // 其他类型保持不变
        }
    }
}

