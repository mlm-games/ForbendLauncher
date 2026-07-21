package org.mlm.forbendlauncher.animation

import android.view.View
import org.mlm.forbendlauncher.apps.BannerSelectedChangedListener
import org.mlm.forbendlauncher.apps.BannerView

class AppViewFocusAnimator(view: BannerView) : ViewFocusAnimator(view), BannerSelectedChangedListener {
    private var selected = false

    override fun onSelectedChanged(v: BannerView, selected: Boolean) {
        if (v == mTargetView) {
            this.selected = selected
            setHasFocus(selected)
        }
    }

    fun onEditModeChanged(v: BannerView, editMode: Boolean) {
        if (v == mTargetView && !editMode) setHasFocus(v.hasFocus())
    }

    override fun onFocusChange(v: View, hasFocus: Boolean) {
        if (v == mTargetView) {
            val banner = v as BannerView
            if (!banner.isEditMode || selected) super.onFocusChange(v, hasFocus)
        }
    }
}