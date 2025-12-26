package com.amazon.tv.leanbacklauncher.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter

inline fun Animator.addListener(
    crossinline onStart: (Animator) -> Unit = {},
    crossinline onEnd: (Animator) -> Unit = {},
    crossinline onCancel: (Animator) -> Unit = {},
    crossinline onRepeat: (Animator) -> Unit = {}
): Animator.AnimatorListener {
    val listener = object : AnimatorListenerAdapter() {
        override fun onAnimationStart(animation: Animator) = onStart(animation)
        override fun onAnimationEnd(animation: Animator) = onEnd(animation)
        override fun onAnimationCancel(animation: Animator) = onCancel(animation)
        override fun onAnimationRepeat(animation: Animator) = onRepeat(animation)
    }
    addListener(listener)
    return listener
}