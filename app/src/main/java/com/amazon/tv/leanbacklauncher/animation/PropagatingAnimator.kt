package com.amazon.tv.leanbacklauncher.animation

import android.animation.ValueAnimator
import android.view.View

abstract class PropagatingAnimator<VH : PropagatingAnimator.ViewHolder>(
    initialCapacity: Int = 10
) : ValueAnimator(), Resettable {

    open class ViewHolder(val view: View) {
        var rawStartDelay: Long = 0
        var normalizedStartDelay: Long = 0
    }

    fun interface Propagation<VH : ViewHolder> {
        fun getStartDelay(holder: VH): Long
    }

    private val views = ArrayList<VH>(initialCapacity)
    private var propagation: Propagation<VH> = Propagation { 0 }
    private var maxStartDelay = 0L
    private var normalized = false
    private var state: Byte = STATE_IDLE

    init {
        setFloatValues(0f, 1f)
        addUpdateListener { onUpdate() }
        addListener(
            onStart = { state = STATE_RUNNING },
            onCancel = { state = STATE_FINISHED; reset() },
            onEnd = { state = STATE_FINISHED }
        )
    }

    protected abstract fun onSetupStartValues(holder: VH)
    protected abstract fun onUpdateView(holder: VH, fraction: Float)
    protected abstract fun onResetView(holder: VH)

    fun setPropagation(propagation: Propagation<VH>) = apply { this.propagation = propagation }

    fun addView(holder: VH) = apply {
        views.add(holder)
        holder.rawStartDelay = propagation.getStartDelay(holder)
        normalized = false
        
        when {
            isStarted -> {
                normalizeStartDelays()
                val fraction = ((currentPlayTime - holder.normalizedStartDelay).toFloat() / childAnimationDuration)
                    .coerceIn(0f, 1f)
                if (fraction <= 0f) onSetupStartValues(holder) 
                else onUpdateView(holder, interpolator.getInterpolation(fraction))
            }
            state == STATE_SETUP -> onSetupStartValues(holder)
        }
    }

    fun removeView(index: Int): VH {
        val holder = views.removeAt(index)
        if (holder.normalizedStartDelay == 0L || holder.normalizedStartDelay == maxStartDelay) {
            normalized = false
        }
        if (isStarted) normalizeStartDelays()
        if (state == STATE_SETUP || state == STATE_FINISHED) onResetView(holder)
        return holder
    }

    protected fun invalidateView(holder: VH) {
        holder.rawStartDelay = propagation.getStartDelay(holder)
        normalized = false
        if (isStarted) normalizeStartDelays()
    }

    fun getView(index: Int) = views[index]
    fun size() = views.size

    val childAnimationDuration: Long
        get() {
            if (!normalized) normalizeStartDelays()
            return duration - maxStartDelay
        }

    override fun reset() {
        if (state == STATE_SETUP || state == STATE_RUNNING) {
            cancel()
            return
        }
        views.forEach { onResetView(it) }
        state = STATE_RESET
    }

    override fun setDuration(duration: Long): PropagatingAnimator<VH> {
        check(!isStarted) { "Can't alter duration after start" }
        super.setDuration(duration)
        views.forEach { invalidateView(it) }
        return this
    }

    override fun start() {
        if (!normalized) normalizeStartDelays()
        setupStartValues()
        state = STATE_SETUP
        super.start()
    }

    override fun setupStartValues() {
        if (state != STATE_SETUP) {
            views.forEach { onSetupStartValues(it) }
            state = STATE_SETUP
        }
    }

    private fun normalizeStartDelays() {
        normalized = true
        if (views.isEmpty()) {
            maxStartDelay = 0
            return
        }
        val minRawDelay = views.minOf { it.rawStartDelay }
        maxStartDelay = views.maxOf { (it.rawStartDelay - minRawDelay).also { d -> it.normalizedStartDelay = it.rawStartDelay - minRawDelay } }
    }

    private fun onUpdate() {
        val duration = childAnimationDuration
        val totalPlayTime = currentPlayTime
        views.forEach { holder ->
            val fraction = ((totalPlayTime - holder.normalizedStartDelay).toFloat() / duration).coerceIn(0f, 1f)
            if (fraction >= 0f) onUpdateView(holder, interpolator.getInterpolation(fraction))
        }
    }

    companion object {
        private const val STATE_IDLE: Byte = 1
        private const val STATE_SETUP: Byte = 2
        private const val STATE_RUNNING: Byte = 8
        private const val STATE_FINISHED: Byte = 16
        private const val STATE_RESET: Byte = 32
    }
}