package com.amazon.tv.leanbacklauncher.animation

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.Keep
import androidx.core.animation.addListener
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.capabilities.LauncherConfiguration
import androidx.core.view.isVisible

open class ViewFocusAnimator(view: View) : View.OnFocusChangeListener {
    
    @JvmField protected var mTargetView: View = view
    
    private var enabled = true
    private var listener: OnFocusLevelChangeListener? = null
    
    private val unselectedScale: Float
    private val selectedScaleDelta: Float
    private val unselectedZ: Float
    private val selectedZDelta: Float
    private val cardElevationSupported: Boolean
    
    private val focusAnimation: ObjectAnimator
    
    @set:Keep
    var focusProgress: Float = 0f
        set(level) {
            field = level
            val scale = unselectedScale + selectedScaleDelta * level
            mTargetView.scaleX = scale
            mTargetView.scaleY = scale
            if (cardElevationSupported) mTargetView.z = unselectedZ + selectedZDelta * level
            listener?.onFocusLevelChange(level)
        }

    val focusedScaleFactor: Float
        get() = mTargetView.resources.getFraction(R.fraction.lb_focus_zoom_factor_medium, 1, 1)

    init {
        val res = view.resources
        mTargetView.onFocusChangeListener = this
        
        unselectedScale = res.getFraction(R.fraction.unselected_scale, 1, 1)
        selectedScaleDelta = focusedScaleFactor - unselectedScale
        unselectedZ = res.getDimensionPixelOffset(R.dimen.unselected_item_z).toFloat()
        selectedZDelta = res.getDimensionPixelOffset(R.dimen.selected_item_z_delta).toFloat()
        cardElevationSupported = LauncherConfiguration.instance?.isCardElevationEnabled == true
        
        focusAnimation = ObjectAnimator.ofFloat(this, "focusProgress", 0f).apply {
            duration = res.getInteger(R.integer.item_scale_anim_duration).toLong()
            interpolator = AccelerateDecelerateInterpolator()
            addListener(onEnd = {
                mTargetView.setHasTransientState(false)
                listener?.onFocusLevelSettled(focusProgress > 0.5f)
            }, onStart = {
                mTargetView.setHasTransientState(true)
            })
        }
    }

    fun setOnFocusProgressListener(listener: OnFocusLevelChangeListener?) { this.listener = listener }

    fun animateFocus(focused: Boolean) {
        if (!enabled) return setFocusImmediate(focused)
        
        focusAnimation.takeIf { it.isStarted }?.cancel()
        val target = if (focused) 1f else 0f
        if (focusProgress != target) {
            focusAnimation.setFloatValues(focusProgress, target)
            focusAnimation.start()
        }
    }

    fun setFocusImmediate(focused: Boolean) {
        focusAnimation.takeIf { it.isStarted }?.cancel()
        focusProgress = if (focused) 1f else 0f
        listener?.onFocusLevelSettled(focused)
    }

    override fun onFocusChange(v: View, hasFocus: Boolean) {
        if (v === mTargetView) setHasFocus(hasFocus)
    }

    protected fun setHasFocus(hasFocus: Boolean) {
        val canAnimate = enabled && mTargetView.run {
            isVisible && isAttachedToWindow && hasWindowFocus()
        }
        if (canAnimate) animateFocus(hasFocus) else setFocusImmediate(hasFocus)
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) focusAnimation.takeIf { it.isStarted }?.end()
    }

    interface OnFocusLevelChangeListener {
        fun onFocusLevelChange(level: Float)
        fun onFocusLevelSettled(focused: Boolean)
    }
}