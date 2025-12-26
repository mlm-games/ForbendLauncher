package com.amazon.tv.tvrecommendations.service

abstract class RankerParameters {
    private var lastVersionToken: Any? = null
    private var spreadFactor = SPREAD_FACTOR_DEFAULT
    private var groupStarterScore = GROUP_STARTER_SCORE_DEFAULT
    private var installBonus = INSTALL_BONUS_DEFAULT
    private var outOfBoxBonus = OUT_OF_BOX_BONUS_DEFAULT
    private var bonusFadePeriodDays = BONUS_FADE_PERIOD_DAYS_DEFAULT

    protected abstract fun getFloat(key: String, default: Float): Float
    protected abstract fun getVersionToken(): Any

    private fun checkUpdateFlags() {
        val versionToken = getVersionToken()
        if (versionToken != lastVersionToken) {
            lastVersionToken = versionToken
            spreadFactor = getFloat("rec_ranker_spread_factor", SPREAD_FACTOR_DEFAULT)
            groupStarterScore = getFloat("rec_ranker_group_starter_score", GROUP_STARTER_SCORE_DEFAULT)
            installBonus = getFloat("rec_ranker_install_bonus", INSTALL_BONUS_DEFAULT)
            outOfBoxBonus = getFloat("rec_ranker_out_of_box_bonus", OUT_OF_BOX_BONUS_DEFAULT)
            bonusFadePeriodDays = getFloat("bonus_fade_period_days", BONUS_FADE_PERIOD_DAYS_DEFAULT)
        }
    }

    fun getOutOfBoxBonus() = checkUpdateFlags().let { outOfBoxBonus }
    fun getGroupStarterScore() = checkUpdateFlags().let { groupStarterScore }
    fun getInstallBonus() = checkUpdateFlags().let { installBonus }
    fun getSpreadFactor() = checkUpdateFlags().let { spreadFactor }
    fun getBonusFadePeriodDays() = checkUpdateFlags().let { bonusFadePeriodDays }

    companion object {
        const val BONUS_FADE_PERIOD_DAYS_DEFAULT = 0.5f
        const val GROUP_STARTER_SCORE_DEFAULT = 0.001f
        const val INSTALL_BONUS_DEFAULT = 0.3f
        const val OUT_OF_BOX_BONUS_DEFAULT = 0.005f
        const val SPREAD_FACTOR_DEFAULT = 1.0f
    }
}

fun interface RankerParametersFactory {
    fun create(context: android.content.Context): RankerParameters
}