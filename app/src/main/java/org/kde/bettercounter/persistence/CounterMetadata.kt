package org.kde.bettercounter.persistence

enum class CounterType {
    STANDARD,
    DYNAMIC
}

data class CounterMetadata(
    var name: String,
    var interval: Interval,
    var goal: Int,
    var color: CounterColor,
    var category: String = "默认",
    var type: CounterType = CounterType.STANDARD,
    var formula: String? = null,
    var step: Int = 1
)

