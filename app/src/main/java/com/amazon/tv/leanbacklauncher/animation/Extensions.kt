package com.amazon.tv.leanbacklauncher.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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


/**
 * View Extensions
 */
fun View.visible() {
    visibility = View.VISIBLE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun View.breath(
    minAlpha: Float = 0.3f,
    maxAlpha: Float = 1.0f,
    duration: Long = 2000L
) {
    val animation = AlphaAnimation(minAlpha, maxAlpha).apply {
        this.duration = duration
        repeatCount = Animation.INFINITE
        repeatMode = Animation.REVERSE
    }
    startAnimation(animation)
}

fun View.fadeIn(duration: Long = 300L) {
    alpha = 0f
    visible()
    animate()
        .alpha(1f)
        .setDuration(duration)
        .start()
}

fun View.fadeOut(duration: Long = 300L, onEnd: (() -> Unit)? = null) {
    animate()
        .alpha(0f)
        .setDuration(duration)
        .withEndAction {
            gone()
            onEnd?.invoke()
        }
        .start()
}

/**
 * Context Extensions
 */
fun Context.registerReceiverCompat(
    receiver: android.content.BroadcastReceiver,
    filter: android.content.IntentFilter,
    exported: Boolean = false
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val flags = if (exported) {
            Context.RECEIVER_EXPORTED
        } else {
            Context.RECEIVER_NOT_EXPORTED
        }
        registerReceiver(receiver, filter, flags)
    } else {
        registerReceiver(receiver, filter)
    }
}

fun Context.unregisterReceiverSafe(receiver: android.content.BroadcastReceiver?) {
    receiver?.let {
        try {
            unregisterReceiver(it)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
}

fun Context.startActivitySafe(intent: Intent): Boolean {
    return try {
        startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * Flow Extensions for collecting in lifecycle-aware manner
 */
fun <T> Flow<T>.collectIn(
    scope: CoroutineScope,
    action: suspend (T) -> Unit
) {
    scope.launch {
        collect { action(it) }
    }
}

inline fun Fragment.launchAndRepeatWithViewLifecycle(
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    crossinline block: suspend CoroutineScope.() -> Unit
) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.lifecycle.repeatOnLifecycle(minActiveState) {
            block()
        }
    }
}

/**
 * Boolean Extensions
 */
inline fun Boolean.ifTrue(block: () -> Unit): Boolean {
    if (this) block()
    return this
}

inline fun Boolean.ifFalse(block: () -> Unit): Boolean {
    if (!this) block()
    return this
}

/**
 * String Extensions
 */
fun String?.orEmpty(): String = this ?: ""

fun String?.isNotNullOrBlank(): Boolean = !this.isNullOrBlank()

/**
 * Collection Extensions
 */
inline fun <T> List<T>.forEachIndexedReversed(action: (index: Int, T) -> Unit) {
    for (index in lastIndex downTo 0) {
        action(index, this[index])
    }
}