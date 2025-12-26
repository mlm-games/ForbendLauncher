package com.amazon.tv.tvrecommendations.service

import android.util.SparseArray
import java.util.Date

class ActiveDayBuffer(private val length: Int) {
    private val buffer = SparseArray<Signals>(length + 1)
    private var dirty = true
    private var cachedScore = -1.0

    operator fun set(date: Date, value: Signals) {
        buffer.put(DateUtil.getDay(date), value)
        while (buffer.size() > length) buffer.removeAt(0)
        dirty = true
    }

    operator fun get(date: Date): Signals? = buffer[DateUtil.getDay(date)]
    
    fun getAt(index: Int): Signals? = 
        if (index in 0 until buffer.size()) buffer.valueAt(index) else null
    
    fun getDayAt(index: Int): Int = 
        if (index in 0 until buffer.size()) buffer.keyAt(index) else -1

    fun size() = length
    fun hasData() = buffer.size() > 0

    fun getAggregatedScore(aggregator: Aggregator<Signals>): Double {
        if (!dirty) return cachedScore
        
        aggregator.reset()
        if (buffer.size() == 0) {
            cachedScore = Ranker.getGroupStarterScore()
        } else {
            repeat(buffer.size()) { i ->
                aggregator.add(DateUtil.getDate(buffer.keyAt(i))!!, buffer.valueAt(i))
            }
            cachedScore = aggregator.getAggregatedScore()
        }
        dirty = false
        return cachedScore
    }
}
