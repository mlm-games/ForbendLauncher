package com.amazon.tv.tvrecommendations.service

class Bucket(var timestamp: Long) {
    val buffer = ActiveDayBuffer(14)
    fun updateTimestamp() { timestamp = System.currentTimeMillis() }
}