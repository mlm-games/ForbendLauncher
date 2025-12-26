package com.amazon.tv.leanbacklauncher.animation

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.leanback.widget.HorizontalGridView
import com.amazon.tv.leanbacklauncher.R

// MassFadeAnimator
class MassFadeAnimator private constructor(builder: Builder) : 
    PropagatingAnimator<MassFadeAnimator.VH>(), Joinable {

    class VH(view: View) : ViewHolder(view)
    enum class Direction { FADE_IN, FADE_OUT }
    interface Participant

    private val root = builder.root
    private val direction = builder.direction
    private val targetClass = builder.targetClass
    private val rows = mutableListOf<HorizontalGridView>()
    
    private val startAlpha = if (direction == Direction.FADE_IN) 0f else 1f
    private val endAlpha = if (direction == Direction.FADE_IN) 1f else 0f

    init { if (builder.duration > 0) setDuration(builder.duration) }

    override fun setupStartValues() {
        if (size() == 0) {
            addViewsFrom(root)
            rows.forEach { it.setAnimateChildLayout(false) }
        }
        super.setupStartValues()
    }

    override fun reset() {
        rows.forEach { it.setAnimateChildLayout(true) }
        super.reset()
    }

    override fun include(target: View) { addView(VH(target)) }
    override fun exclude(target: View) { (0 until size()).find { getView(it).view == target }?.let { removeView(it) } }

    override fun onSetupStartValues(holder: VH) { holder.view.alpha = startAlpha }
    override fun onUpdateView(holder: VH, fraction: Float) { holder.view.alpha = startAlpha + (endAlpha - startAlpha) * fraction }
    override fun onResetView(holder: VH) { holder.view.alpha = 1f }

    private fun addViewsFrom(group: ViewGroup) {
        repeat(group.childCount) { i ->
            val child = group.getChildAt(i)
            if (targetClass.isInstance(child)) addView(VH(child))
            (child as? ViewGroup)?.let { addViewsFrom(it) }
            (child as? HorizontalGridView)?.let { rows.add(it) }
        }
    }

    class Builder(val root: ViewGroup) {
        var direction = Direction.FADE_OUT
        var targetClass: Class<*> = Participant::class.java
        var duration = -1L
        
        fun setDirection(d: Direction) = apply { direction = d }
        fun setTarget(c: Class<*>) = apply { targetClass = c }
        fun setDuration(d: Long) = apply { duration = d }
        fun build() = MassFadeAnimator(this)
    }
}

// MassSlideAnimator
class MassSlideAnimator private constructor(builder: Builder) : 
    PropagatingAnimator<MassSlideAnimator.VH>(), Joinable {

    enum class Direction { SLIDE_IN, SLIDE_OUT }

    class VH(
        view: View, 
        root: ViewGroup, 
        epicenter: Rect, 
        direction: Direction
    ) : ViewHolder(view) {
        val center = IntArray(2)
        val side: Int
        val startY: Float
        val endY: Float

        init {
            view.getLocationOnScreen(center)
            center[0] += view.width / 2
            center[1] += view.height / 2
            side = if (center[1] > epicenter.centerY()) 2 else 1
            
            val scaleFactor = generateSequence(view) { it.parent as? View }
                .takeWhile { it != root }
                .fold(1f) { acc, v -> acc * v.scaleY }
            
            val endY = (if (side == 1) -root.height else root.height) / scaleFactor
            this.startY = if (direction == Direction.SLIDE_IN) endY else 0f
            this.endY = if (direction == Direction.SLIDE_IN) 0f else endY
        }
    }

    private val root = builder.root
    private val epicenter = builder.epicenter
    private val direction = builder.direction
    private val exclude = builder.exclude
    private val excludeClass = builder.excludeClass
    private val fade = builder.fade
    private val rows = mutableListOf<HorizontalGridView>()

    init {
        val res = root.resources
        setPropagation(SlidePropagation())
        interpolator = if (direction == Direction.SLIDE_IN) SLIDE_IN_INTERPOLATOR else SLIDE_OUT_INTERPOLATOR
        setDuration(res.getInteger(R.integer.slide_animator_default_duration).toLong())
    }

    override fun setupStartValues() {
        if (size() == 0) {
            addViewsFrom(root)
            rows.forEach { it.setAnimateChildLayout(false) }
        }
        super.setupStartValues()
    }

    override fun reset() {
        rows.forEach { it.setAnimateChildLayout(true) }
        super.reset()
        while (size() > 0) removeView(size() - 1)
    }

    override fun include(target: View) { addView(VH(target, root, epicenter, direction)) }
    override fun exclude(target: View) { (0 until size()).find { getView(it).view == target }?.let { removeView(it) } }

    override fun onSetupStartValues(holder: VH) = onUpdateView(holder, 0f)
    
    override fun onUpdateView(holder: VH, fraction: Float) {
        holder.view.translationY = holder.startY + fraction * (holder.endY - holder.startY)
        if (fade) holder.view.alpha = if (direction == Direction.SLIDE_IN) fraction else 1f - fraction
    }

    override fun onResetView(holder: VH) {
        holder.view.translationY = 0f
        if (fade) holder.view.alpha = 1f
    }

    private fun addViewsFrom(group: ViewGroup) {
        repeat(group.childCount) { i ->
            val child = group.getChildAt(i)
            if (child is ParticipatesInLaunchAnimation && !isExcluded(child)) {
                addView(VH(child, root, epicenter, direction))
            }
            (child as? ViewGroup)?.let { addViewsFrom(it) }
            (child as? HorizontalGridView)?.let { rows.add(it) }
        }
    }

    private fun isExcluded(view: View) = view == exclude || excludeClass?.isInstance(view) == true

    private inner class SlidePropagation : Propagation<VH> {
        private val windowHeight: Int = Rect().also { root.getWindowVisibleDisplayFrame(it) }.height()

        override fun getStartDelay(holder: VH): Long {
            val epicenterX = epicenter.centerX()
            val distance = when (holder.side) {
                1 -> (windowHeight - holder.center[1]) + kotlin.math.abs(epicenterX - holder.center[0])
                2 -> holder.center[1] + kotlin.math.abs(epicenterX - holder.center[0])
                else -> 0
            }.coerceIn(0, windowHeight)
            return (duration / PROPAGATION_SPEED * distance / windowHeight).toLong()
        }
    }

    class Builder(val root: ViewGroup) {
        var direction = Direction.SLIDE_OUT
        var epicenter = Rect()
        var exclude: View? = null
        var excludeClass: Class<*>? = null
        var fade = true

        fun setDirection(d: Direction) = apply { direction = d }
        fun setEpicenter(r: Rect) = apply { epicenter = r }
        fun setExclude(v: View) = apply { exclude = v }
        fun setExclude(c: Class<*>) = apply { excludeClass = c }
        fun setFade(f: Boolean) = apply { fade = f }
        fun build() = MassSlideAnimator(this)
    }

    companion object {
        private const val PROPAGATION_SPEED = 5.8f
        private val SLIDE_IN_INTERPOLATOR = PathInterpolator(0f, 0f, 0.2f, 1f)
        private val SLIDE_OUT_INTERPOLATOR = PathInterpolator(0.4f, 0f, 1f, 1f)
    }
}