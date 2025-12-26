package com.amazon.tv.leanbacklauncher.animation

import android.graphics.Point
import android.graphics.Rect
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.notifications.HomeScreenView
import com.amazon.tv.leanbacklauncher.notifications.NotificationCardView
import kotlin.math.ceil
import kotlin.math.sqrt

class CircleTakeoverAnimator(
    target: View,
    private val circleLayerView: ImageView,
    color: Int
) : ForwardingAnimator<android.animation.Animator>(createReveal(target, circleLayerView, color)) {

    private var finished = false

    init {
        mDelegate.addListener(
            onStart = { circleLayerView.visibility = View.VISIBLE },
            onCancel = { reset() },
            onEnd = { finished = true }
        )
    }

    override fun reset() { circleLayerView.visibility = View.INVISIBLE }
    override fun isStarted() = !finished && super.isStarted()

    companion object {
        private fun createReveal(target: View, circleView: ImageView, color: Int): android.animation.Animator {
            val wm = circleView.context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
            val screenSize = Point().also { wm.defaultDisplay.getSize(it) }
            
            val pos = IntArray(2).also { target.getLocationInWindow(it) }
            val scale = target.scaleX
            val x = (pos[0] + target.measuredWidth * scale / 2).toInt()
            val y = (pos[1] + target.measuredHeight * scale / 2).toInt()
            val w = screenSize.x - x
            val h = screenSize.y - y
            
            val r = maxOf(
                ceil(sqrt((x * x + y * y).toDouble())),
                ceil(sqrt((w * w + y * y).toDouble())),
                ceil(sqrt((w * w + h * h).toDouble())),
                ceil(sqrt((x * x + h * h).toDouble()))
            ).toInt()
            
            circleView.setBackgroundColor(-16777216 or color)
            circleView.alpha = 1f
            return ViewAnimationUtils.createCircularReveal(circleView, x, y, 0f, r.toFloat())
        }
    }
}

class LauncherLaunchAnimator(
    root: ViewGroup,
    cause: View,
    epicenter: Rect,
    circleLayerView: ImageView,
    color: Int,
    headers: Array<View>,
    homeScreenView: HomeScreenView?
) : ForwardingAnimatorSet() {
    init {
        val res = root.resources
        val fadeDuration = res.getInteger(R.integer.app_launch_animation_header_fade_out_duration).toLong()
        val fadeDelay = res.getInteger(R.integer.app_launch_animation_header_fade_out_delay).toLong()

        val circle = CircleTakeoverAnimator(cause, circleLayerView, color).apply {
            setDuration(res.getInteger(R.integer.app_launch_animation_explode_duration).toLong())
        }
        val causeFade = FadeAnimator(cause, FadeAnimator.Direction.FADE_OUT).apply {
            duration = res.getInteger(R.integer.app_launch_animation_target_fade_duration).toLong()
            startDelay = res.getInteger(R.integer.app_launch_animation_target_fade_delay).toLong()
        }
        val slide = MassSlideAnimator.Builder(root).setEpicenter(epicenter).setExclude(cause).setFade(false).build()

        mDelegate.play(circle).with(causeFade).with(slide)

        homeScreenView?.takeIf { !it.isRowViewVisible }?.let {
            mDelegate.play(FadeAnimator(it, FadeAnimator.Direction.FADE_OUT).apply {
                duration = fadeDuration; startDelay = fadeDelay
            })
        }

        headers.forEach {
            mDelegate.play(FadeAnimator(it, FadeAnimator.Direction.FADE_OUT).apply {
                duration = fadeDuration; startDelay = fadeDelay
            })
        }
    }
}

class LauncherReturnAnimator(
    root: ViewGroup,
    epicenter: Rect,
    headers: Array<View>,
    homeScreenView: HomeScreenView?
) : ForwardingAnimatorSet() {
    init {
        val res = root.resources
        val fadeDuration = res.getInteger(R.integer.app_launch_animation_header_fade_in_duration).toLong()
        val fadeDelay = res.getInteger(R.integer.app_launch_animation_header_fade_in_delay).toLong()

        val isNotificationFocused = root.findFocus() is NotificationCardView

        val builder = if (isNotificationFocused) {
            mDelegate.play(MassSlideAnimator.Builder(root)
                .setEpicenter(epicenter)
                .setDirection(MassSlideAnimator.Direction.SLIDE_IN)
                .setExclude(NotificationCardView::class.java)
                .setFade(false).build()
            ).also {
                it.with(MassFadeAnimator.Builder(root)
                    .setDirection(MassFadeAnimator.Direction.FADE_IN)
                    .setTarget(NotificationCardView::class.java)
                    .setDuration(res.getInteger(R.integer.app_launch_animation_rec_fade_duration).toLong())
                    .build())
            }
        } else {
            mDelegate.play(MassSlideAnimator.Builder(root)
                .setEpicenter(epicenter)
                .setDirection(MassSlideAnimator.Direction.SLIDE_IN)
                .setFade(false).build()
            ).also { b ->
                homeScreenView?.takeIf { !it.isRowViewVisible }?.let {
                    b.with(FadeAnimator(it, FadeAnimator.Direction.FADE_IN).apply {
                        duration = fadeDuration; startDelay = fadeDelay
                    })
                }
            }
        }

        headers.forEach {
            builder.with(FadeAnimator(it, FadeAnimator.Direction.FADE_IN).apply {
                duration = fadeDuration; startDelay = fadeDelay
            })
        }
    }
}

class LauncherDismissAnimator(root: ViewGroup, fade: Boolean, headers: Array<View>) : ForwardingAnimatorSet() {
    init {
        val res = root.resources
        val fadeDuration = res.getInteger(R.integer.app_launch_animation_header_fade_out_duration).toLong()
        val fadeDelay = res.getInteger(R.integer.app_launch_animation_header_fade_out_delay).toLong()

        val builder = mDelegate.play(MassSlideAnimator.Builder(root)
            .setDirection(MassSlideAnimator.Direction.SLIDE_OUT)
            .setFade(fade).build())

        headers.forEach {
            builder.with(FadeAnimator(it, FadeAnimator.Direction.FADE_OUT).apply {
                duration = fadeDuration; startDelay = fadeDelay
            })
        }
    }
}

class LauncherPauseAnimator(root: ViewGroup) : ForwardingAnimatorSet() {
    init {
        mDelegate.play(FadeAnimator(root, FadeAnimator.Direction.FADE_OUT).apply {
            duration = root.resources.getInteger(R.integer.launcher_pause_animation_duration).toLong()
        })
    }
}

class NotificationLaunchAnimator(
    root: ViewGroup,
    cause: NotificationCardView,
    epicenter: Rect,
    circleLayerView: ImageView,
    color: Int,
    headers: Array<View>,
    homeScreenView: HomeScreenView?
) : ForwardingAnimatorSet() {
    init {
        val res = root.resources
        val fadeDuration = res.getInteger(R.integer.app_launch_animation_header_fade_out_duration).toLong()
        val fadeDelay = res.getInteger(R.integer.app_launch_animation_header_fade_out_delay).toLong()

        val circle = CircleTakeoverAnimator(cause, circleLayerView, color).apply {
            setDuration(res.getInteger(R.integer.app_launch_animation_explode_duration).toLong())
        }

        mDelegate.play(circle)
            .with(MassFadeAnimator.Builder(root)
                .setDirection(MassFadeAnimator.Direction.FADE_OUT)
                .setTarget(NotificationCardView::class.java)
                .setDuration(res.getInteger(R.integer.app_launch_animation_rec_fade_duration).toLong()).build())
            .with(MassSlideAnimator.Builder(root)
                .setEpicenter(epicenter)
                .setExclude(cause)
                .setExclude(NotificationCardView::class.java)
                .setFade(false).build())

        headers.forEach {
            mDelegate.play(FadeAnimator(it, FadeAnimator.Direction.FADE_OUT).apply {
                duration = fadeDuration; startDelay = fadeDelay
            })
        }
    }
}