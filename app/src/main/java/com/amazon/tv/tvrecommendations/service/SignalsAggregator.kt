package com.amazon.tv.tvrecommendations.service

import java.util.Date

class SignalsAggregator : Aggregator<Signals> {
    private var totalClicks = 0
    private var totalImpressions = 0

    override fun reset() {
        totalClicks = 0
        totalImpressions = 0
    }

    override fun add(date: Date, value: Signals) {
        totalClicks += value.mClicks
        totalImpressions += value.mImpressions
    }

    override fun getAggregatedScore(): Double {
        if (totalImpressions == 0) return 0.0
        return totalClicks.toDouble() / totalImpressions
    }
}