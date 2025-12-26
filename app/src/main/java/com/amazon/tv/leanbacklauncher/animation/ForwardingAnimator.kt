package com.amazon.tv.leanbacklauncher.animation

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.TimeInterpolator
import android.view.View

abstract class ForwardingAnimator<T : Animator>(
    protected val mDelegate: T
) : Animator(), Joinable, Resettable {

    private val listenerMap = mutableMapOf<AnimatorListener, AnimatorListener>()
    private val pauseListenerMap = mutableMapOf<AnimatorPauseListener, AnimatorPauseListener>()

    override fun reset() { (mDelegate as? Resettable)?.reset() }
    override fun include(target: View) { (mDelegate as? Joinable)?.include(target) }
    override fun exclude(target: View) { (mDelegate as? Joinable)?.exclude(target) }

    override fun start() = mDelegate.start()
    override fun cancel() = mDelegate.cancel()
    override fun end() = mDelegate.end()
    override fun pause() = mDelegate.pause()
    override fun resume() = mDelegate.resume()
    override fun isPaused() = mDelegate.isPaused
    override fun isRunning() = mDelegate.isRunning
    override fun isStarted() = mDelegate.isStarted

    override fun getStartDelay() = mDelegate.startDelay
    override fun setStartDelay(startDelay: Long) { mDelegate.startDelay = startDelay }
    override fun getDuration() = mDelegate.duration
    override fun setDuration(duration: Long) = apply { mDelegate.duration = duration }
    override fun getInterpolator(): TimeInterpolator = mDelegate.interpolator
    override fun setInterpolator(value: TimeInterpolator?) { mDelegate.interpolator = value }

    override fun setupEndValues() = mDelegate.setupEndValues()
    override fun setupStartValues() = mDelegate.setupStartValues()
    override fun setTarget(target: Any?) = mDelegate.setTarget(target)

    override fun addListener(listener: AnimatorListener) {
        if (listener !in listenerMap) {
            val proxy = ProxyListener(listener)
            listenerMap[listener] = proxy
            mDelegate.addListener(proxy)
        }
    }

    override fun removeListener(listener: AnimatorListener) {
        listenerMap.remove(listener)?.let { mDelegate.removeListener(it) }
    }

    override fun getListeners(): ArrayList<AnimatorListener> = ArrayList(listenerMap.keys)

    override fun addPauseListener(listener: AnimatorPauseListener) {
        if (listener !in pauseListenerMap) {
            val proxy = ProxyPauseListener(listener)
            pauseListenerMap[listener] = proxy
            mDelegate.addPauseListener(proxy)
        }
    }

    override fun removePauseListener(listener: AnimatorPauseListener) {
        pauseListenerMap.remove(listener)?.let { mDelegate.removePauseListener(it) }
    }

    override fun removeAllListeners() {
        mDelegate.removeAllListeners()
        listenerMap.clear()
        pauseListenerMap.clear()
    }

    private inner class ProxyListener(private val delegate: AnimatorListener) : AnimatorListener {
        override fun onAnimationStart(a: Animator) = delegate.onAnimationStart(this@ForwardingAnimator)
        override fun onAnimationEnd(a: Animator) = delegate.onAnimationEnd(this@ForwardingAnimator)
        override fun onAnimationCancel(a: Animator) = delegate.onAnimationCancel(this@ForwardingAnimator)
        override fun onAnimationRepeat(a: Animator) = delegate.onAnimationRepeat(this@ForwardingAnimator)
    }

    private inner class ProxyPauseListener(private val delegate: AnimatorPauseListener) : AnimatorPauseListener {
        override fun onAnimationPause(a: Animator) = delegate.onAnimationPause(this@ForwardingAnimator)
        override fun onAnimationResume(a: Animator) = delegate.onAnimationResume(this@ForwardingAnimator)
    }
}

abstract class ForwardingAnimatorSet : ForwardingAnimator<AnimatorSet>(AnimatorSet()) {
    
    override fun reset() {
        mDelegate.childAnimations.filterIsInstance<Resettable>().forEach { it.reset() }
    }

    override fun include(target: View) {
        mDelegate.childAnimations.filterIsInstance<Joinable>().forEach { it.include(target) }
    }

    override fun exclude(target: View) {
        mDelegate.childAnimations.filterIsInstance<Joinable>().forEach { it.exclude(target) }
    }
}