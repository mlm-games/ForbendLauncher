package com.amazon.tv.leanbacklauncher.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.amazon.tv.leanbacklauncher.R

class PlayingIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barWidthPx = context.resources.getDimensionPixelSize(R.dimen.leanback_card_now_playing_bar_width)
    private val barSeparationPx = context.resources.getDimensionPixelSize(R.dimen.leanback_card_now_playing_bar_margin)
    private val drawRect = Rect()
    private val paint = Paint().apply { color = -1 }
    
    private var progress = 0f
    var isPlaying = false

    private val animator = ValueAnimator().apply {
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        duration = 100_000_000L
        setFloatValues(0f, duration / 80f)
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = barWidthPx * 3 + barSeparationPx * 2
        setMeasuredDimension(size, size)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimationIfVisible()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    fun stopAnimation() {
        animator.cancel()
        postInvalidate()
    }

    fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    fun startAnimationIfVisible() {
        if (visibility == VISIBLE) {
            animator.start()
            postInvalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        repeat(3) { barIndex ->
            drawRect.left = (barWidthPx + barSeparationPx) * barIndex
            drawRect.right = drawRect.left + barWidthPx
            drawRect.bottom = height
            
            val level = if (isPlaying) {
                linearlyInterpolateWithWrapping(progress, LEVELS[barIndex])
            } else {
                0.5f
            }
            drawRect.top = ((height * (15f - level)) / 15f).toInt()
            
            canvas.drawRect(drawRect, paint)
        }
    }

    companion object {
        private val LEVELS = arrayOf(
            intArrayOf(5, 3, 5, 7, 9, 10, 11, 12, 11, 12, 10, 8, 7, 4, 2, 4, 6, 7, 9, 11, 9, 7, 5, 3, 5, 8, 5, 3, 4),
            intArrayOf(12, 11, 10, 11, 12, 11, 9, 7, 9, 11, 12, 10, 8, 10, 12, 11, 9, 5, 3, 5, 8, 10, 12, 10, 9, 8),
            intArrayOf(8, 9, 10, 12, 11, 9, 7, 5, 7, 8, 9, 12, 11, 12, 9, 7, 9, 11, 12, 10, 8, 9, 7, 5, 3)
        )

        private fun linearlyInterpolateWithWrapping(position: Float, array: IntArray): Float {
            val positionRounded = position.toInt()
            val beforeIndex = positionRounded % array.size
            val weight = position - positionRounded
            val afterIndex = (beforeIndex + 1) % array.size
            return array[beforeIndex] * (1f - weight) + array[afterIndex] * weight
        }
    }
}