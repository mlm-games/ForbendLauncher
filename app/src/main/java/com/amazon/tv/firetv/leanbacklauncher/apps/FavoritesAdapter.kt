package org.mlm.forbendlauncher.apps

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import org.mlm.forbendlauncher.util.SharedPreferencesUtil
import org.mlm.forbendlauncher.util.SharedPreferencesUtil.Companion.instance
import org.mlm.forbendlauncher.apps.AppsAdapter
import org.mlm.forbendlauncher.apps.LaunchPoint

/**
 * Created by rockon999 on 3/7/18.
 */
class FavoritesAdapter(
    context: Context,
    actionOpenLaunchPointListener: ActionOpenLaunchPointListener?,
    vararg appTypes: AppCategory?
) : AppsAdapter(context, actionOpenLaunchPointListener, *appTypes),
    OnSharedPreferenceChangeListener {
    private val prefUtil: SharedPreferencesUtil? = instance(context)
    private val listener: OnSharedPreferenceChangeListener = this
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        refreshDataSetAsync()
    }

    internal inner class FavoritesAppFilter : AppFilter() {
        override fun include(point: LaunchPoint?): Boolean {
            return prefUtil != null && prefUtil.isFavorite(point?.packageName)
        }
    }

    init {
        mFilter = FavoritesAppFilter()
        this.prefUtil?.addFavoritesListener(listener)
    }
}