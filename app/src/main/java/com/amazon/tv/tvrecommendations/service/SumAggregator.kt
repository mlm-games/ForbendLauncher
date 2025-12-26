package com.amazon.tv.tvrecommendations.service

import java.util.Date

class SumAggregator : Aggregator<Signals> {
    private var sum = 0.0
    
    override fun reset() { sum = 0.0 }
    override fun add(date: Date, value: Signals) { sum += value.mClicks }
    override fun getAggregatedScore() = sum
}