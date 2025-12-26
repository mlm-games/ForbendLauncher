package com.amazon.tv.leanbacklauncher.animation

import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.TextView
import com.amazon.tv.leanbacklauncher.EditableAppsRowView
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.apps.BannerView
import com.amazon.tv.leanbacklauncher.widget.EditModeView

class EditModeUninstallAnimationHolder(editMode: EditModeView) {
    private var animationDuration: Long
    private var bannerAnimation: TranslateAnimation? = null
    private val uninstallBanner: View = editMode.uninstallApp!!
    private val uninstallCircle: View = editMode.uninstallCircle!!
    private val uninstallIcon: View = editMode.uninstallIcon!!
    private val uninstallText: TextView = editMode.uninstallText!!
    private val uninstallIconCircle: View = editMode.uninstallIconCircle!!

    enum class EditModeUninstallState {
        ENTER, EXIT
    }

    init {
        animationDuration = uninstallBanner.resources.getInteger(R.integer.edit_mode_uninstall_anim_duration).toLong()
    }

    fun startAnimation(state: EditModeUninstallState, curBanner: BannerView, activeItems: EditableAppsRowView) {
        addBannerDragAnimation(state, curBanner, activeItems)
        uninstallBanner.startAnimation(bannerAnimation)
        animateIconCircle(state)
        animateUninstallText(state)
        animateUninstallCircle(state)
        animateUninstallIcon(state)
    }

    fun setViewsToExitState() {
        val tempDuration = animationDuration
        animationDuration = 0
        animateIconCircle(EditModeUninstallState.EXIT)
        animateUninstallText(EditModeUninstallState.EXIT)
        animateUninstallCircle(EditModeUninstallState.EXIT)
        animateUninstallIcon(EditModeUninstallState.EXIT)
        animationDuration = tempDuration
    }

    private fun addBannerDragAnimation(state: EditModeUninstallState, curBanner: BannerView, activeItems: EditableAppsRowView) {
        val curLocation = IntArray(2).apply { curBanner.getLocationOnScreen(this) }
        val destLocation = IntArray(2).apply { uninstallBanner.getLocationOnScreen(this) }
        
        val res = uninstallBanner.resources
        val scaleDelta = res.getFraction(R.fraction.lb_focus_zoom_factor_medium, 1, 1) - 
                        res.getFraction(R.fraction.unselected_scale, 1, 1)

        val fromX = if (state == EditModeUninstallState.ENTER) (curLocation[0] - destLocation[0]).toFloat() else 0f
        val toX = if (state == EditModeUninstallState.ENTER) 0f else 
                  (curLocation[0] - destLocation[0]) - (curBanner.width * scaleDelta / 2)
        
        val fromY = if (state == EditModeUninstallState.ENTER) (curLocation[1] - destLocation[1]).toFloat() else 0f
        val toY = if (state == EditModeUninstallState.ENTER) 0f else 
                  (curLocation[1] - destLocation[1]) - (curBanner.height * scaleDelta / 2)

        bannerAnimation = TranslateAnimation(fromX, toX, fromY, toY).apply {
            duration = this@EditModeUninstallAnimationHolder.animationDuration
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(a: Animation) {
                    uninstallBanner.visibility = View.VISIBLE
                    if (state == EditModeUninstallState.ENTER) activeItems.setBannerDrawableUninstallState(true)
                }
                override fun onAnimationEnd(a: Animation) {
                    if (state == EditModeUninstallState.ENTER) {
                        uninstallBanner.visibility = View.VISIBLE
                    } else {
                        uninstallBanner.visibility = View.GONE
                        activeItems.setBannerDrawableUninstallState(false)
                    }
                }
                override fun onAnimationRepeat(a: Animation) {}
            })
        }
    }

    private fun animateIconCircle(state: EditModeUninstallState) {
        uninstallIconCircle.animate().duration = animationDuration
        if (state == EditModeUninstallState.ENTER) {
            uninstallIconCircle.animate().scaleX(1f).scaleY(1f).alpha(1f)
        } else {
            uninstallIconCircle.animate().scaleX(0f).scaleY(0f).alpha(0f)
        }
    }

    private fun animateUninstallText(state: EditModeUninstallState) {
        uninstallText.animate().duration = animationDuration
        uninstallText.animate().alpha(if (state == EditModeUninstallState.ENTER) 1f else 0f)
    }

    private fun animateUninstallCircle(state: EditModeUninstallState) {
        val res = uninstallCircle.resources
        val focusWidth = res.getDimensionPixelSize(R.dimen.edit_uninstall_area_circle_focus_width).toFloat()
        val focusHeight = res.getDimensionPixelSize(R.dimen.edit_uninstall_area_circle_focus_height).toFloat()
        val unfocusWidth = res.getDimensionPixelSize(R.dimen.edit_uninstall_area_circle_width).toFloat()
        val unfocusHeight = res.getDimensionPixelSize(R.dimen.edit_uninstall_area_circle_height).toFloat()

        uninstallCircle.animate().duration = animationDuration
        if (state == EditModeUninstallState.ENTER) {
            uninstallCircle.animate()
                .scaleX(focusWidth / unfocusWidth)
                .scaleY(focusHeight / unfocusHeight)
                .alpha(0.15f)
        } else {
            uninstallCircle.animate().scaleX(1f).scaleY(1f).alpha(0.05f)
        }
    }

    private fun animateUninstallIcon(state: EditModeUninstallState) {
        val res = uninstallIcon.resources
        val focusSize = res.getDimensionPixelSize(R.dimen.edit_uninstall_icon_focused_size).toFloat()
        val unfocusSize = res.getDimensionPixelSize(R.dimen.edit_uninstall_icon_unfocused_size).toFloat()
        val bottomMargin = res.getDimensionPixelSize(R.dimen.edit_uninstall_icon_circle_focused_bottom_margin).toFloat()

        val circleLoc = IntArray(2).apply { uninstallIconCircle.getLocationOnScreen(this) }
        val iconLoc = IntArray(2).apply { uninstallIcon.getLocationOnScreen(this) }

        uninstallIcon.animate().duration = animationDuration
        if (state == EditModeUninstallState.ENTER) {
            uninstallIcon.animate()
                .scaleX(focusSize / unfocusSize)
                .scaleY(focusSize / unfocusSize)
                .translationY((circleLoc[1] - iconLoc[1] - focusSize + bottomMargin))
        } else {
            uninstallIcon.animate().scaleX(1f).scaleY(1f).translationY(0f)
        }
    }
}