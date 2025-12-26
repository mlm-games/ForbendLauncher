package com.amazon.tv.leanbacklauncher.trace

object AppTrace {
    interface TraceTag

    fun beginSection(section: String) {
        // Debug logging if needed: Log.d("AppTrace", section)
    }

    fun endSection() {
        // Debug logging if needed
    }

    fun beginAsyncSection(section: String): TraceTag? = null

    fun endAsyncSection(tag: TraceTag?) {}
}