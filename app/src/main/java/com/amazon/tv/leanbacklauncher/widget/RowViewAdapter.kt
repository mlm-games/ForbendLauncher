package com.amazon.tv.leanbacklauncher.widget

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.amazon.tv.leanbacklauncher.MainActivity

abstract class RowViewAdapter<VH : RecyclerView.ViewHolder>(
    protected val mContext: Context
) : RecyclerView.Adapter<VH>() {

    override fun onViewDetachedFromWindow(holder: VH) {
        super.onViewDetachedFromWindow(holder)
        (mContext as? MainActivity)?.excludeFromLaunchAnimation(holder.itemView)
    }

    override fun onViewAttachedToWindow(holder: VH) {
        super.onViewAttachedToWindow(holder)
        val activity = mContext as? MainActivity ?: return
        
        if (activity.isLaunchAnimationInProgress) {
            holder.itemView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View, left: Int, top: Int, right: Int, bottom: Int,
                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                ) {
                    v.removeOnLayoutChangeListener(this)
                    activity.includeInLaunchAnimation(holder.itemView)
                }
            })
        }
    }
}