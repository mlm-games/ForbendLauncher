package org.mlm.forbendlauncher.notifications

import org.mlm.forbendlauncher.TvRecommendation

internal object NotificationUtils {
    @JvmStatic
    fun equals(left: TvRecommendation?, right: TvRecommendation?): Boolean {
        return if (left == null || right == null) {
            left == right
        } else left.key.equals(right.key)
    }
}