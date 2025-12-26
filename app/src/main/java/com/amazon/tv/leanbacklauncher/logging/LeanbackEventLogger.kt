package com.amazon.tv.leanbacklauncher.logging

import android.content.Context
import android.util.Log

class LeanbackLauncherEventLogger private constructor(context: Context) {

    fun flush(): Boolean = false
    fun logEvent(tag: String, event: Any) {}

    companion object {
        private const val TAG = "LeanbackLauncherEventLogger"

        @Volatile
        private var instance: LeanbackLauncherEventLogger? = null

        @Synchronized
        fun getInstance(context: Context): LeanbackLauncherEventLogger? {
            return instance ?: try {
                LeanbackLauncherEventLogger(context.applicationContext).also { instance = it }
            } catch (t: Throwable) {
                Log.e(TAG, "Exception creating LeanbackLauncherEventLogger", t)
                null
            }
        }
    }
}
