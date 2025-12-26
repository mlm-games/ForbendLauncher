// Interfaces.kt - Combine all simple interfaces
package com.amazon.tv.leanbacklauncher.animation

import android.view.View

fun interface Resettable {
    fun reset()
}

interface Joinable {
    fun include(target: View)
    fun exclude(target: View)
}

interface ParticipatesInLaunchAnimation
interface ParticipatesInScrollAnimation {
    fun setAnimationsEnabled(enabled: Boolean)
}