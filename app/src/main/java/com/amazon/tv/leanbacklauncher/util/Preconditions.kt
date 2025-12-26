package com.amazon.tv.leanbacklauncher.util

object Preconditions {
    @JvmStatic
    fun <T> checkNotNull(reference: T?): T = reference ?: throw NullPointerException()

    @JvmStatic
    fun checkState(expression: Boolean) {
        if (!expression) throw IllegalStateException()
    }

    @JvmStatic
    fun checkArgument(expression: Boolean) {
        if (!expression) throw IllegalArgumentException()
    }
}