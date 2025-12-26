package com.amazon.tv.tvrecommendations.service

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Environment
import java.io.File
import java.util.*

class AppUsageStatistics(private val context: Context) {
    
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var appUsageScore: Map<String, Double>? = null
    private var lastCallTime = 0L
    
    private val privilegedAppDir = runCatching { 
        File(Environment.getRootDirectory(), "priv-app").canonicalPath 
    }.getOrNull()

    fun getAppUsageScore(packageName: String): Double {
        val now = System.currentTimeMillis()
        if (appUsageScore == null || now > lastCallTime + 1800000) {
            appUsageScore = getAppUsageAdjustments()
            lastCallTime = now
        }
        return appUsageScore?.get(packageName) ?: 0.0
    }

    private fun getAppUsageAdjustments(): Map<String, Double> {
        val histogram = mutableMapOf<String, Long>()
        val cal = Calendar.getInstance()
        val to = cal.timeInMillis

        // Last day
        cal.add(Calendar.DAY_OF_YEAR, -1)
        addToHistogram(histogram, usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, to))
        
        // Last week
        cal.add(Calendar.DAY_OF_YEAR, -6)
        addToHistogram(histogram, usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, cal.timeInMillis, to))
        
        // Last month
        cal.add(Calendar.DAY_OF_YEAR, -23)
        addToHistogram(histogram, usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, cal.timeInMillis, to))

        // Remove privileged apps
        getInstalledPrivApps().forEach { histogram.remove(it) }

        val totalTime = histogram.values.sum()
        return if (totalTime > 0) {
            histogram.mapValues { it.value.toDouble() / totalTime }
        } else emptyMap()
    }

    private fun getInstalledPrivApps(): List<String> {
        val pm = context.packageManager
        return pm.getInstalledPackages(0).mapNotNull { pi ->
            runCatching {
                val ai = pm.getApplicationInfo(pi.packageName, 0)
                if ((ai.flags and 1) != 0 && ai.publicSourceDir?.startsWith(privilegedAppDir ?: "") == true) {
                    pi.packageName
                } else null
            }.getOrNull()
        }
    }

    private fun addToHistogram(histogram: MutableMap<String, Long>, stats: List<android.app.usage.UsageStats>) {
        stats.forEach { pkgStats ->
            val tif = pkgStats.totalTimeInForeground
            if (tif > 0) {
                histogram.merge(pkgStats.packageName, tif, Long::plus)
            }
        }
    }
}