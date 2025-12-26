package com.amazon.tv.leanbacklauncher.animation

import android.view.View
import android.view.ViewGroup
import com.amazon.tv.leanbacklauncher.ActiveFrame
import com.amazon.tv.leanbacklauncher.EditableAppsRowView
import com.amazon.tv.leanbacklauncher.HomeScreenRow
import com.amazon.tv.leanbacklauncher.MainActivity
import com.amazon.tv.leanbacklauncher.R

class EditModeMassFadeAnimator(
    activity: MainActivity,
    private val editMode: EditMode
) : PropagatingAnimator<EditModeMassFadeAnimator.ViewHolder>(), Joinable {

    enum class EditMode { ENTER, EXIT }
    enum class Direction { FADE_IN, FADE_OUT }

    class ViewHolder(
        view: View,
        val direction: Direction,
        val onOffOnly: Boolean = false
    ) : PropagatingAnimator.ViewHolder(view) {
        val startAlpha = if (direction == Direction.FADE_IN) 0f else 1f
        val endAlpha = if (direction == Direction.FADE_IN) 1f else 0f
    }

    init {
        val res = activity.resources
        duration = (if (editMode == EditMode.EXIT) 
            res.getInteger(R.integer.edit_mode_exit_fade_duration) 
        else 
            res.getInteger(R.integer.edit_mode_entrance_fade_duration)).toLong()
        addViews(activity)
    }

    private fun addViews(activity: MainActivity) {
        // Add Home Screen Rows
        activity.homeAdapter?.allRows?.forEach { row ->
            val activeFrame = (row as? HomeScreenRow)?.rowView as? ActiveFrame ?: return@forEach
            
            for (i in 0 until activeFrame.childCount) {
                val rowView = activeFrame.getChildAt(i)
                val isEditable = rowView is EditableAppsRowView && rowView.editMode
                
                if (!isEditable) {
                    addView(ViewHolder(rowView, if (editMode == EditMode.ENTER) Direction.FADE_OUT else Direction.FADE_IN))
                }
            }
        }

        // Add Wallpaper and Edit Mode Views
        activity.wallpaperView?.let {
            addView(ViewHolder(it, if (editMode == EditMode.ENTER) Direction.FADE_OUT else Direction.FADE_IN))
        }
        activity.editModeView?.let {
            addView(ViewHolder(it, if (editMode == EditMode.ENTER) Direction.FADE_IN else Direction.FADE_OUT))
        }
        activity.editModeWallpaper?.let {
            addView(ViewHolder(it, if (editMode == EditMode.ENTER) Direction.FADE_IN else Direction.FADE_OUT, true))
        }
    }

    override fun onSetupStartValues(holder: ViewHolder) {
        if (holder.onOffOnly) {
            holder.view.visibility = if (holder.startAlpha == 0f) View.INVISIBLE else View.VISIBLE
            holder.view.alpha = if (holder.startAlpha == 0f) 0f else 1f
        } else {
            holder.view.alpha = holder.startAlpha
        }
    }

    override fun onUpdateView(holder: ViewHolder, fraction: Float) {
        val alpha = holder.startAlpha + (holder.endAlpha - holder.startAlpha) * fraction
        if (holder.onOffOnly) {
            holder.view.visibility = if (alpha == 0f) View.INVISIBLE else View.VISIBLE
            holder.view.alpha = if (alpha == 0f) 0f else 1f
        } else {
            holder.view.alpha = alpha
        }
    }

    override fun onResetView(holder: ViewHolder) {
        if (holder.onOffOnly) {
            holder.view.visibility = if (holder.direction == Direction.FADE_IN) View.INVISIBLE else View.VISIBLE
            holder.view.alpha = if (holder.direction == Direction.FADE_IN) 0f else 1f
        } else {
            holder.view.alpha = 1f
        }
    }

    override fun include(target: View) {
        var editModeParticipant = false
        if (target is ActiveFrame) {
            for (i in 0 until target.childCount) {
                val child = target.getChildAt(i)
                if (child is EditableAppsRowView && child.editMode) {
                    editModeParticipant = true
                    break
                }
            }
        }

        val direction = if ((editModeParticipant && editMode == EditMode.ENTER) || 
                           (!editModeParticipant && editMode == EditMode.EXIT)) {
            Direction.FADE_IN
        } else {
            Direction.FADE_OUT
        }
        addView(ViewHolder(target, direction))
    }

    override fun exclude(target: View) {
        (0 until size()).find { getView(it).view == target }?.let { removeView(it) }
    }
}