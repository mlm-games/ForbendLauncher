package com.amazon.tv.leanbacklauncher.animation

import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.Keep
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.widget.PlayingIndicatorView

class ViewDimmer(private val targetView: View) {

    enum class DimState { ACTIVE, INACTIVE, EDIT_MODE }

    private val activeDimLevel = targetView.resources.getFraction(R.fraction.launcher_active_dim_level, 1, 1)
    private val inactiveDimLevel = targetView.resources.getFraction(R.fraction.launcher_inactive_dim_level, 1, 1)
    private val editModeDimLevel = targetView.resources.getFraction(R.fraction.launcher_edit_mode_dim_level, 1, 1)

    private var animationEnabled = true
    private var concatMatrix: ColorMatrix? = null
    private var dimState: DimState? = null

    private val imageViews = mutableListOf<ImageView>()
    private val desatImageViews = mutableListOf<ImageView>()
    private val drawables = mutableListOf<Drawable>()
    private val desatDrawables = mutableListOf<Drawable>()
    private val textViews = mutableListOf<Pair<TextView, Int>>()
    private val playingIndicatorViews = mutableListOf<PlayingIndicatorView>()

    private val dimAnimation = ObjectAnimator.ofFloat(this, "dimLevel", inactiveDimLevel).apply {
        duration = targetView.resources.getInteger(R.integer.item_dim_anim_duration).toLong()
        addListener(
            onStart = { targetView.setHasTransientState(true) },
            onEnd = { targetView.setHasTransientState(false) }
        )
    }

    @set:Keep
    var dimLevel: Float = 0f
        private set(level) {
            field = level
            val filterIndex = (255 * level).toInt().coerceIn(0, 255)
            
            val filter = when {
                level in 0f..1f -> concatMatrix?.let { 
                    ColorMatrixColorFilter(ColorMatrix().apply { setConcat(sMatrices[filterIndex], it) })
                } ?: sFilters[filterIndex]
                concatMatrix != null -> ColorMatrixColorFilter(concatMatrix!!)
                else -> null
            }
            
            val desatFilter = if (level in 0f..1f) sFiltersDesat[filterIndex] else null

            imageViews.forEach { it.colorFilter = filter }
            desatImageViews.forEach { it.colorFilter = desatFilter }
            drawables.forEach { it.colorFilter = filter }
            desatDrawables.forEach { it.mutate().colorFilter = desatFilter }
            playingIndicatorViews.forEach { it.setColorFilter(filter) }
            textViews.forEach { (tv, origColor) -> tv.setTextColor(getDimmedColor(origColor, level)) }
        }

    fun setAnimationEnabled(enabled: Boolean) {
        animationEnabled = enabled
        if (!enabled && dimAnimation.isStarted) dimAnimation.end()
    }

    fun setConcatMatrix(matrix: ColorMatrix?) { concatMatrix = matrix }

    fun convertToDimLevel(state: DimState) = when (state) {
        DimState.ACTIVE -> activeDimLevel
        DimState.INACTIVE -> inactiveDimLevel
        DimState.EDIT_MODE -> editModeDimLevel
    }

    fun animateDim(state: DimState) {
        if (!animationEnabled) return setDimLevelImmediate(state)
        
        dimAnimation.takeIf { it.isStarted }?.cancel()
        val target = convertToDimLevel(state)
        if (dimLevel != target) {
            dimAnimation.setFloatValues(dimLevel, target)
            dimAnimation.start()
        }
    }

    fun setDimLevelImmediate(state: DimState) {
        dimAnimation.takeIf { it.isStarted }?.cancel()
        dimLevel = convertToDimLevel(state)
    }

    fun setDimState(state: DimState, immediate: Boolean) {
        if (immediate) setDimLevelImmediate(state) else animateDim(state)
        dimState = state
    }

    // Add targets
    fun addDimTarget(view: ImageView) { imageViews += view }
    fun addDesatDimTarget(view: ImageView) { desatImageViews += view }
    fun addDimTarget(drawable: Drawable) { drawables += drawable }
    fun addDesatDimTarget(drawable: Drawable) { desatDrawables += drawable }
    fun addDimTarget(view: PlayingIndicatorView) { playingIndicatorViews += view }
    fun addDimTarget(view: TextView) { textViews += view to view.currentTextColor }

    fun removeDimTarget(drawable: Drawable) { drawables -= drawable }
    fun removeDesatDimTarget(drawable: Drawable) { desatDrawables -= drawable }

    fun setTargetTextColor(view: TextView, newColor: Int) {
        val index = textViews.indexOfFirst { it.first == view }
        if (index >= 0) {
            textViews[index] = view to newColor
            view.setTextColor(getDimmedColor(newColor, dimLevel))
        }
    }

    companion object {
        private val sFilters = arrayOfNulls<ColorFilter>(256)
        private val sFiltersDesat = arrayOfNulls<ColorFilter>(256)
        private val sMatrices = arrayOfNulls<ColorMatrix>(256)

        init {
            val desat = ColorMatrix().apply { setSaturation(0f) }
            repeat(256) { i ->
                val dimVal = 1f - i / 255f
                sMatrices[i] = ColorMatrix().apply { setScale(dimVal, dimVal, dimVal, 1f) }
                sFilters[i] = ColorMatrixColorFilter(sMatrices[i]!!)
                sFiltersDesat[i] = ColorMatrixColorFilter(ColorMatrix().apply {
                    setScale(dimVal, dimVal, dimVal, 1f)
                    postConcat(desat)
                })
            }
        }

        fun getDimmedColor(color: Int, level: Float): Int {
            val factor = 1f - level
            return Color.argb(
                Color.alpha(color),
                (Color.red(color) * factor).toInt(),
                (Color.green(color) * factor).toInt(),
                (Color.blue(color) * factor).toInt()
            )
        }

        fun activatedToDimState(activated: Boolean) = if (activated) DimState.ACTIVE else DimState.INACTIVE
        fun dimStateToActivated(state: DimState) = state != DimState.INACTIVE
    }
}