package org.mlm.forbendlauncher.service

class Bucket(var timestamp: Long) {
    val buffer = ActiveDayBuffer(14)
    fun updateTimestamp() { timestamp = System.currentTimeMillis() }
}