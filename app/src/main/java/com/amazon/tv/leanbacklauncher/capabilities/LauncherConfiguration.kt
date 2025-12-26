package com.amazon.tv.leanbacklauncher.capabilities

abstract class LauncherConfiguration {
    abstract val isCardElevationEnabled: Boolean
    abstract val isLegacyRecommendationLayoutEnabled: Boolean
    abstract val isRichRecommendationViewEnabled: Boolean
    abstract val isRoundCornersEnabled: Boolean

    companion object {
        @Volatile
        var instance: LauncherConfiguration? = null
            private set

        fun setInstance(config: LauncherConfiguration) {
            instance = config
        }
    }
}

object HighEndLauncherConfiguration : LauncherConfiguration() {
    override val isCardElevationEnabled = true
    override val isRichRecommendationViewEnabled = true
    override val isLegacyRecommendationLayoutEnabled = false
    override val isRoundCornersEnabled = true
}