package com.amazon.tv.leanbacklauncher.animation

import android.animation.ValueAnimator
import android.view.View

class FadeAnimator(
    private val target: View,
    direction: Direction
) : ValueAnimator(), Resettable {

    enum class Direction { FADE_IN, FADE_OUT }

    private val startAlpha = if (direction == Direction.FADE_IN) 0f else 1f

    init {
        val endAlpha = if (direction == Direction.FADE_IN) 1f else 0f
        setFloatValues(startAlpha, endAlpha)
        addUpdateListener { target.alpha = animatedValue as Float }
        addListener(onCancel = { reset() })
    }

    override fun setupStartValues() { target.alpha = startAlpha }
    override fun reset() { target.alpha = 1f }

    override fun toString() = "FadeAnimator@${hashCode().toString(16)}:${if (startAlpha == 0f) "FADE_IN" else "FADE_OUT"}"
}