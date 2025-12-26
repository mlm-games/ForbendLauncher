package com.amazon.tv.firetv.leanbacklauncher.apps

sealed class RowType(val code: Int) {
    object SEARCH : RowType(0)
    object NOTIFICATIONS : RowType(1)
    object PARTNER : RowType(2)
    object APPS : RowType(3)
    object GAMES : RowType(4)
    object SETTINGS : RowType(5)
    object INPUTS : RowType(6)
    object FAVORITES : RowType(7)
    object MUSIC : RowType(8)
    object VIDEO : RowType(9)
    object ACTUAL_NOTIFICATIONS : RowType(10)

    companion object {
        fun fromRowCode(code: Int): RowType {
            return when (code) {
                0 -> SEARCH
                1 -> NOTIFICATIONS
                2 -> PARTNER
                3 -> APPS
                4 -> GAMES
                5 -> SETTINGS
                6 -> INPUTS
                7 -> FAVORITES
                8 -> MUSIC
                9 -> VIDEO
                10 -> ACTUAL_NOTIFICATIONS
                else -> throw IllegalArgumentException("Unknown row code: $code")
            }
        }

        fun fromRowCodeOrNull(code: Int): RowType? {
            return try {
                fromRowCode(code)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RowType) return false
        return code == other.code
    }

    override fun hashCode(): Int {
        return code
    }

    override fun toString(): String {
        return when (this) {
            SEARCH -> "SEARCH"
            NOTIFICATIONS -> "NOTIFICATIONS"
            PARTNER -> "PARTNER"
            APPS -> "APPS"
            GAMES -> "GAMES"
            SETTINGS -> "SETTINGS"
            INPUTS -> "INPUTS"
            FAVORITES -> "FAVORITES"
            MUSIC -> "MUSIC"
            VIDEO -> "VIDEO"
            ACTUAL_NOTIFICATIONS -> "ACTUAL_NOTIFICATIONS"
        }
    }
}