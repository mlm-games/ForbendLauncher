package com.amazon.tv.leanbacklauncher.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import java.io.PrintWriter

class AnimatorLifecycle : Joinable, Resettable {

    interface OnAnimationFinishedListener {
        fun onAnimationFinished()
    }
    val lastKnownEpicenter = Rect()

    private var animation: Animator? = null
    private var callback: Runnable? = null
    private var flags: Byte = 0
    private var onAnimationFinishedListener: (() -> Unit)? = null
    private val recentAnimationDumps = ArrayDeque<String>(10)

    private val handler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == MSG_START && isPrimed) start()
        true
    }

    fun <T> init(animation: T, callback: Runnable?, flags: Byte) where T : Animator, T : Resettable {
        if (this.animation != null) {
            Log.w(TAG, "Called to initialize an animation that was already initialized")
            reset()
        }
        this.animation = animation
        this.callback = callback
        this.flags = flags
        setState(STATE_INIT)
    }

    fun schedule() {
        check(isInitialized)
        setState(STATE_SCHEDULED)
    }

    fun prime() {
        check(isScheduled)
        animation?.setupStartValues()
        setState(STATE_PRIMED)
        handler.sendEmptyMessageDelayed(MSG_START, 1000)
    }

    fun start() {
        check(isInitialized || isScheduled || isPrimed)
        animation?.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) { cancelled = true }

            override fun onAnimationEnd(animation: Animator) {
                animation.removeListener(this)
                setState(STATE_FINISHED)

                if (this@AnimatorLifecycle.animation == null) {
                    Log.w(TAG, "listener notified of animation end when mAnimation==null")
                    (animation as? Resettable)?.reset()
                }

                onAnimationFinishedListener?.invoke()

                when {
                    cancelled -> reset()
                    callback != null -> runCatching { callback?.run() }
                        .onFailure { Log.e(TAG, "Could not execute callback", it); reset() }
                }

                if (flags.toInt() and FLAG_AUTO_RESET != 0) reset()
            }
        })

        animation?.start()
        setState(STATE_RUNNING)

        recentAnimationDumps.apply {
            while (size >= 10) removeLast()
            addFirst(animation.toString())
        }
    }

    fun cancel() { if (isRunning) animation?.cancel() }

    override fun reset() {
        cancel()
        (animation as? Resettable)?.reset()
        flags = 0
        animation = null
        callback = null
        handler.removeMessages(MSG_START)
    }

    val isInitialized get() = animation != null && flags.toInt() and STATE_INIT != 0
    val isScheduled get() = animation != null && flags.toInt() and STATE_SCHEDULED != 0
    val isPrimed get() = animation != null && flags.toInt() and STATE_PRIMED != 0
    val isRunning get() = animation != null && flags.toInt() and STATE_RUNNING != 0
    val isFinished get() = animation != null && flags.toInt() and STATE_FINISHED != 0

    fun setOnAnimationFinishedListener(listener: (() -> Unit)?) { onAnimationFinishedListener = listener }

    override fun include(target: View) { (animation as? Joinable)?.include(target) }
    override fun exclude(target: View) { (animation as? Joinable)?.exclude(target) }

    private fun setState(state: Int) {
        flags = (flags.toInt() and 0xE0 or state).toByte()
        handler.removeMessages(MSG_START)
    }

    fun dump(prefix: String, writer: PrintWriter, root: ViewGroup?) {
        val stateName = when (flags.toInt() and 0x1F) {
            STATE_INIT -> "INIT"
            STATE_SCHEDULED -> "SCHEDULED"
            STATE_PRIMED -> "PRIMED"
            STATE_RUNNING -> "RUNNING"
            STATE_FINISHED -> "FINISHED"
            else -> "<idle>"
        }
        writer.println("${prefix}AnimatorLifecycle State: $stateName")
        writer.println("${prefix}lastKnownEpicenter: ${lastKnownEpicenter.centerX()},${lastKnownEpicenter.centerY()}")
        writer.println("${prefix}mAnimation: ${animation?.toString()?.replace("\n", "\n$prefix")}")
        root?.let { dumpViewHierarchy(prefix, writer, it) }
    }

    private fun dumpViewHierarchy(prefix: String, writer: PrintWriter, view: View) {
        if (view is ParticipatesInLaunchAnimation) {
            writer.println("$prefix${view.toShortString()}")
        }
        (view as? ViewGroup)?.let { group ->
            repeat(group.childCount) { dumpViewHierarchy(prefix, writer, group.getChildAt(it)) }
        }
    }

    private fun View.toShortString() = buildString {
        append("${this@toShortString::class.simpleName}@${Integer.toHexString(System.identityHashCode(this@toShortString))}")
        append("{α=%.1f Δy=%.1f s=%.1fx%.1f ".format(alpha, translationY, scaleX, scaleY))
        append(if (isFocused) "F" else ".")
        append(if (isSelected) "S" else ".")
        append("}")
    }

    companion object {
        private const val TAG = "Animations"
        private const val MSG_START = 1
        private const val STATE_INIT = 1
        private const val STATE_SCHEDULED = 2
        private const val STATE_PRIMED = 4
        private const val STATE_RUNNING = 8
        private const val STATE_FINISHED = 16
        private const val FLAG_AUTO_RESET = 32
    }
}

@Deprecated("Old api")
inline fun <reified T> AnimatorLifecycle.schedule() {
    // Member schedule() is chosen (no type args), this extension is only for schedule<T>()
    schedule()
}