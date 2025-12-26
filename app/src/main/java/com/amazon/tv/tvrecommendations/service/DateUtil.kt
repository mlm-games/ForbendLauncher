package com.amazon.tv.tvrecommendations.service

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.*

object DateUtil {
    private const val PREF_KEY = "recommendations_oob_ranking_marker"
    
    fun getDay(date: Date?): Int {
        date ?: return -1
        return Calendar.getInstance().apply { time = date }.let {
            it[Calendar.YEAR] * 1000 + it[Calendar.DAY_OF_YEAR]
        }
    }

    fun getDate(day: Int): Date? {
        if (day == -1) return null
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, day / 1000)
            set(Calendar.DAY_OF_YEAR, day % 1000)
        }.time
    }

    fun initialRankingApplied(ctx: Context) = 
        PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(PREF_KEY, false)

    fun setInitialRankingAppliedFlag(ctx: Context, applied: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putBoolean(PREF_KEY, applied).apply()
    }
}
