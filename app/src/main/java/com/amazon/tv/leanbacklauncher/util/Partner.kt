package com.amazon.tv.leanbacklauncher.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.Log
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.apps.AppsManager
import java.util.*

class Partner private constructor(
    private val packageName: String?,
    private val receiverName: String?,
    private val resources: Resources?
) {
    private var rowDataReady = false
    private val rowPositions = hashMapOf<String, Int>()

    val systemBackground: Drawable?
        get() {
            if (resources == null || packageName.isNullOrEmpty()) return null
            val nameResId = resources.getIdentifier("partner_wallpaper", "string", packageName)
            if (nameResId == 0) return null
            val name = resources.getString(nameResId)
            if (name.isNullOrEmpty()) return null
            val wallpaperResId = resources.getIdentifier(name, "drawable", packageName)
            return if (wallpaperResId != 0) resources.getDrawable(wallpaperResId, null) else null
        }

    val systemBackgroundMask: Bitmap? get() = getBitmap("partner_wallpaper_bg_mask")
    val systemBackgroundVideoMask: Bitmap? get() = getBitmap("partner_wallpaper_bg_video_mask")

    val appSortingMode: AppsManager.SortingMode
        get() {
            if (resources == null || packageName.isNullOrEmpty()) return AppsManager.SortingMode.FIXED
            val nameResId = resources.getIdentifier("partner_app_sorting_mode", "string", packageName)
            return if (nameResId != 0) {
                AppsManager.SortingMode.valueOf(resources.getString(nameResId).uppercase(Locale.ENGLISH))
            } else {
                if (isRowEnabled("partner_row")) AppsManager.SortingMode.RECENCY else AppsManager.SortingMode.FIXED
            }
        }

    val customSearchIcon: Drawable?
        get() {
            if (resources == null || packageName.isNullOrEmpty()) return null
            val nameResId = resources.getIdentifier("partner_search_icon", "string", packageName)
            if (nameResId == 0) return null
            val name = resources.getString(nameResId)
            if (name.isNullOrEmpty()) return null
            val iconResId = resources.getIdentifier(name, "drawable", packageName)
            return if (iconResId != 0) resources.getDrawable(iconResId, null) else null
        }

    val widgetComponentName: ComponentName?
        get() {
            if (resources == null || packageName.isNullOrEmpty()) return null
            val nameResId = resources.getIdentifier("partner_widget_provider_component_name", "string", packageName)
            return if (nameResId != 0) ComponentName.unflattenFromString(resources.getString(nameResId)) else null
        }

    val partnerFontName: String?
        get() {
            if (resources == null || packageName.isNullOrEmpty()) return null
            val nameResId = resources.getIdentifier("partner_font", "string", packageName)
            if (nameResId == 0) return null
            return resources.getString(nameResId).takeIf { !it.isNullOrEmpty() }
        }

    val outOfBoxOrder: Array<String>?
        get() {
            if (resources == null || packageName.isNullOrEmpty()) return null
            val resId = resources.getIdentifier("partner_out_of_box_order", "array", packageName)
            return if (resId != 0) resources.getStringArray(resId) else null
        }

    val showLiveTvOnStartUp: Boolean
        get() {
            if (resources == null || packageName.isNullOrEmpty()) return false
            val resId = resources.getIdentifier("partner_show_live_tv_on_start_up", "bool", packageName)
            return if (resId != 0) resources.getBoolean(resId) else false
        }

    val showPhysicalTunersSeparately: Boolean
        get() {
            if (resources == null || packageName.isNullOrEmpty()) return false
            val resId = resources.getIdentifier("show_physical_tuners_separately", "bool", packageName)
            return if (resId != 0) resources.getBoolean(resId) else false
        }

    val disableDisconnectedInputs: Boolean
        get() {
            if (resources == null || packageName.isNullOrEmpty()) return true
            val resId = resources.getIdentifier("disable_disconnected_inputs", "bool", packageName)
            return if (resId != 0) resources.getBoolean(resId) else true
        }

    val stateIconFromTVInput: Boolean
        get() {
            if (resources == null || packageName.isNullOrEmpty()) return false
            val resId = resources.getIdentifier("enable_input_state_icon", "bool", packageName)
            return if (resId != 0) resources.getBoolean(resId) else false
        }

    val bundledTunerTitle: String?
        get() {
            if (resources == null || packageName.isNullOrEmpty()) return null
            val nameResId = resources.getIdentifier("bundled_tuner_title", "string", packageName)
            if (nameResId == 0) return null
            return resources.getString(nameResId).takeIf { it.isNotEmpty() }
        }

    val disconnectedInputToastText: String?
        get() {
            if (resources == null || packageName.isNullOrEmpty()) return null
            val textResId = resources.getIdentifier("disconnected_input_text", "string", packageName)
            if (textResId == 0) return null
            return resources.getString(textResId).takeIf { !it.isNullOrEmpty() }
        }

    val bundledTunerBanner: Drawable?
        get() {
            if (resources == null || packageName.isNullOrEmpty()) return null
            val resId = resources.getIdentifier("bundled_tuner_banner", "drawable", packageName)
            return if (resId != 0) resources.getDrawable(resId, null) else null
        }

    val inputsOrderMap: Map<Int, Int>
        get() {
            val map = mutableMapOf<Int, Int>()
            if (resources == null || packageName.isNullOrEmpty()) return map

            val resId = resources.getIdentifier("home_screen_inputs_ordering", "array", packageName)
            if (resId == 0) return map

            val inputsArray = resources.getStringArray(resId)
            var priority = 0
            inputsArray.forEach { input ->
                DEFAULT_PRIORITIES_MAP[input]?.let { type ->
                    map[type] = priority++
                }
            }
            return map
        }

    private fun getBitmap(key: String): Bitmap? {
        if (resources == null || packageName.isNullOrEmpty()) return null
        val keyResId = resources.getIdentifier(key, "string", packageName)
        if (keyResId == 0) return null
        val name = resources.getString(keyResId)
        if (name.isNullOrEmpty()) return null
        val bitmapResId = resources.getIdentifier(name, "drawable", packageName)
        return if (bitmapResId != 0) BitmapFactory.decodeResource(resources, bitmapResId) else null
    }

    fun isRowEnabled(row: String) = getRowPosition(row) != -1

    fun getRowPosition(row: String): Int {
        if (!rowDataReady) fetchRowsData()
        if (!rowDataReady) return -1
        return rowPositions[row.trim().lowercase()] ?: -1
    }

    fun getRowTitle(row: String, defaultValue: String): String {
        if (resources == null || packageName.isNullOrEmpty()) return defaultValue
        val resId = resources.getIdentifier("${row}_title", "string", packageName)
        return if (resId != 0) resources.getString(resId) else defaultValue
    }

    fun getRowIcon(row: String): Drawable? {
        if (resources == null || packageName.isNullOrEmpty()) return null
        val resId = resources.getIdentifier("${row}_icon", "drawable", packageName)
        return if (resId != 0) resources.getDrawable(resId, null) else null
    }

    fun getBundledTunerLabelColorOption(default: Int): Int {
        if (resources == null || packageName.isNullOrEmpty()) return default
        val nameResId = resources.getIdentifier("bundled_tuner_label_color_option", "integer", packageName)
        return if (nameResId != 0) resources.getInteger(nameResId) else default
    }

    @SuppressLint("WrongConstant")
    private fun sendInitBroadcast(context: Context) {
        if (packageName.isNullOrEmpty() || receiverName.isNullOrEmpty()) return
        
        val intent = Intent("com.google.android.leanbacklauncher.action.PARTNER_CUSTOMIZATION").apply {
            component = ComponentName(packageName, receiverName)
            flags = Intent.FLAG_RECEIVER_FOREGROUND
            putExtra("com.google.android.leanbacklauncher.extra.ROW_WRAPPING_CUTOFF",
                context.resources.getInteger(R.integer.two_row_cut_off))
        }
        context.sendBroadcast(intent)
    }

    private fun fetchRowsData() {
        if (resources == null || packageName.isNullOrEmpty()) return

        val resId = resources.getIdentifier(getRowPositionResString(), "array", packageName)
        if (resId == 0) return

        val rowsArray = resources.getStringArray(resId)
        rowPositions.clear()
        
        listOf("partner_row", "apps_row", "games_row", "inputs_row", "settings_row").forEach {
            rowPositions[it] = -1
        }

        var position = 0
        rowsArray.forEach { row ->
            if (rowPositions[row] == -1) {
                rowPositions[row] = position++
            }
        }
        rowDataReady = true
    }

    protected open fun getRowPositionResString() = "home_screen_row_ordering"

    companion object {
        private val lock = Any()
        private var partner: Partner? = null
        private var searched = false

        private val DEFAULT_PRIORITIES_MAP = mapOf(
            "input_type_combined_tuners" to -3,
            "input_type_tuner" to 0,
            "input_type_cec_logical" to -2,
            "input_type_cec_recorder" to -4,
            "input_type_cec_playback" to -5,
            "input_type_mhl_mobile" to -6,
            "input_type_hdmi" to 1007,
            "input_type_dvi" to 1006,
            "input_type_component" to 1004,
            "input_type_svideo" to 1002,
            "input_type_composite" to 1001,
            "input_type_displayport" to 1008,
            "input_type_vga" to 1005,
            "input_type_scart" to 1003,
            "input_type_other" to 1000
        )

        @JvmStatic
        fun get(context: Context): Partner {
            synchronized(lock) {
                if (!searched) {
                    val pm = context.packageManager
                    val info = getPartnerResolveInfo(pm)
                    
                    if (info != null) {
                        val pkgName = info.activityInfo.packageName
                        try {
                            partner = Partner(
                                pkgName,
                                info.activityInfo.name,
                                pm.getResourcesForApplication(pkgName)
                            ).also { it.sendInitBroadcast(context) }
                        } catch (e: PackageManager.NameNotFoundException) {
                            Log.w("Partner", "Failed to find resources for $pkgName")
                        }
                    }
                    
                    searched = true
                    if (partner == null) {
                        partner = Partner(null, null, null)
                    }
                }
                return partner!!
            }
        }

        @JvmStatic
        fun resetIfNecessary(context: Context, packageName: String?) {
            synchronized(lock) {
                if (partner != null && !packageName.isNullOrEmpty() && packageName == partner!!.packageName) {
                    searched = false
                    partner = null
                    get(context)
                }
            }
        }

        private fun getPartnerResolveInfo(pm: PackageManager) =
            pm.queryBroadcastReceivers(
                Intent("com.google.android.leanbacklauncher.action.PARTNER_CUSTOMIZATION"), 0
            ).firstOrNull()
    }
}