package org.mlm.forbendlauncher.service

import java.util.Date

interface Aggregator<T> {
    fun add(date: Date, value: T)
    fun getAggregatedScore(): Double
    fun reset()
}
