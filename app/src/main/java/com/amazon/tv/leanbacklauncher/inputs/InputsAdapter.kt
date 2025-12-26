package com.amazon.tv.leanbacklauncher.inputs

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.amazon.tv.leanbacklauncher.LauncherViewHolder
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.apps.BannerView
import com.amazon.tv.leanbacklauncher.util.Partner
import com.amazon.tv.leanbacklauncher.widget.RowViewAdapter
import java.util.*

class InputsAdapter(
    context: Context,
    private val config: Configuration
) : RowViewAdapter<InputsAdapter.InputViewHolder>(context) {

    data class Configuration(
        val showPhysicalTunersSeparately: Boolean,
        val disableDisconnectedInputs: Boolean,
        val getStateIconFromTVInput: Boolean
    )

    private val tvManager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
    private val inflater = LayoutInflater.from(context)
    private val textColorLight = ContextCompat.getColor(context, R.color.input_banner_label_text_color_light)
    private val textColorDark = ContextCompat.getColor(context, R.color.input_banner_label_text_color_dark)

    private val inputs = hashMapOf<String, TvInputEntry>()
    private val visibleInputs = mutableListOf<TvInputEntry>()
    private val physicalTunerInputs = linkedMapOf<String, TvInputInfo>()
    private val virtualTunerInputs = hashMapOf<String, TvInputInfo>()
    private var isBundledTunerVisible = false

    private val comparator = InputsComparator()
    private val handler = InputsMessageHandler()
    private val inputsCallback = InputCallback()

    private val typePriorities: MutableMap<Int, Int> = setupDeviceTypePriorities()

    init {
        refreshInputs()
        tvManager?.registerCallback(inputsCallback, handler)
    }

    fun unregisterReceivers() {
        tvManager?.unregisterCallback(inputsCallback)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        InputViewHolder(inflater.inflate(R.layout.input_banner, parent, false))

    override fun onBindViewHolder(holder: InputViewHolder, position: Int) {
        visibleInputs.getOrNull(position)?.let { holder.init(it) }
    }

    override fun getItemCount() = visibleInputs.size

    fun refreshInputsData() {
        refreshInputs()
        notifyDataSetChanged()
    }

    private fun refreshInputs() {
        inputs.clear()
        visibleInputs.clear()
        physicalTunerInputs.clear()
        virtualTunerInputs.clear()
        isBundledTunerVisible = false

        if (mContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_TV)) {
            tvManager?.tvInputList?.forEach { inputAdded(it, true) }
            visibleInputs.sortWith(comparator)
        }
    }

    private fun inputAdded(info: TvInputInfo?, isRefresh: Boolean) {
        info ?: return
        
        when {
            info.isPassthroughInput -> addInputEntry(info, isRefresh)
            isPhysicalTuner(mContext.packageManager, info) -> {
                physicalTunerInputs[info.id] = info
                if (config.showPhysicalTunersSeparately) {
                    addInputEntry(info, isRefresh)
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !info.isHidden(mContext)) {
                    showBundledTunerInput(isRefresh)
                }
            }
            else -> {
                virtualTunerInputs[info.id] = info
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !info.isHidden(mContext)) {
                    showBundledTunerInput(isRefresh)
                }
            }
        }
    }

    private fun addInputEntry(input: TvInputInfo, isRefresh: Boolean) {
        try {
            if (inputs[input.id] != null) return
            
            val state = tvManager?.getInputState(input.id) ?: return
            var parentEntry: TvInputEntry? = null
            
            input.parentId?.let { parentId ->
                tvManager?.getTvInputInfo(parentId)?.let { parentInfo ->
                    parentEntry = inputs[parentInfo.id] ?: TvInputEntry(
                        parentInfo, null, tvManager.getInputState(parentInfo.id), mContext
                    ).also { inputs[parentInfo.id] = it }
                    parentEntry?.numChildren = (parentEntry?.numChildren ?: 0) + 1
                }
            }

            val entry = TvInputEntry(input, parentEntry, state, mContext)
            inputs[input.id] = entry

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && entry.info?.isHidden(mContext) == true) return
            if (parentEntry != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && 
                parentEntry!!.info?.isHidden(mContext) == true) return

            if (isRefresh) {
                visibleInputs.add(entry)
                if (parentEntry != null && parentEntry!!.info?.parentId == null && 
                    !visibleInputs.contains(parentEntry)) {
                    visibleInputs.add(parentEntry!!)
                }
            } else {
                notifyItemInserted(insertEntryIntoSortedList(entry, visibleInputs))
                if (parentEntry != null && parentEntry!!.info?.parentId == null && 
                    !visibleInputs.contains(parentEntry)) {
                    notifyItemInserted(insertEntryIntoSortedList(parentEntry!!, visibleInputs))
                }
            }
        } catch (e: IllegalArgumentException) {
            Log.e("InputsAdapter", "Failed to get state for Input. Id = ${input.id}")
        }
    }

    private fun insertEntryIntoSortedList(entry: TvInputEntry, list: MutableList<TvInputEntry>): Int {
        var i = 0
        while (i < list.size && comparator.compare(entry, list[i]) > 0) i++
        if (!list.contains(entry)) list.add(i, entry)
        return i
    }

    private fun showBundledTunerInput(isRefresh: Boolean) {
        if (isBundledTunerVisible) return
        
        val partner = Partner.get(mContext)
        val bundledTuner = TvInputEntry(
            partner.bundledTunerTitle!!,
            partner.bundledTunerBanner,
            partner.getBundledTunerLabelColorOption(0),
            TYPE_BUNDLED_TUNER
        )
        
        if (isRefresh) {
            visibleInputs.add(bundledTuner)
        } else {
            notifyItemInserted(insertEntryIntoSortedList(bundledTuner, visibleInputs))
        }
        isBundledTunerVisible = true
    }

    private fun hideBundledTunerInput(isRefresh: Boolean) {
        if (!isBundledTunerVisible) return
        
        for (i in visibleInputs.indices.reversed()) {
            if (visibleInputs[i].isBundledTuner) {
                visibleInputs.removeAt(i)
                if (!isRefresh) notifyItemRemoved(i)
                isBundledTunerVisible = false
                break
            }
        }
    }

    private fun inputStateUpdated(id: String, state: Int) {
        val entry = inputs[id] ?: return
        val wasConnected = entry.state != TvInputManager.INPUT_STATE_DISCONNECTED
        val isNowConnected = state != TvInputManager.INPUT_STATE_DISCONNECTED
        entry.state = state

        val visPos = visibleInputs.indexOfFirst { it.info?.id == id }
        if (visPos < 0) return

        if (!config.disableDisconnectedInputs || wasConnected == isNowConnected) {
            notifyItemChanged(visPos)
        } else {
            visibleInputs.removeAt(visPos)
            val newPos = insertEntryIntoSortedList(entry, visibleInputs)
            notifyItemMoved(visPos, newPos)
            notifyItemChanged(newPos)
        }
    }

    private fun inputRemoved(id: String) {
        val entry = inputs[id]
        if (entry?.info?.isPassthroughInput != true) {
            removeTuner(id)
        } else {
            removeEntry(id)
        }
    }

    private fun removeTuner(id: String) {
        removeEntry(id)
        virtualTunerInputs.remove(id)
        physicalTunerInputs.remove(id)
        
        if (virtualTunerInputs.isEmpty() && 
            (physicalTunerInputs.isEmpty() || config.showPhysicalTunersSeparately)) {
            hideBundledTunerInput(false)
        }
    }

    private fun removeEntry(id: String) {
        val entry = inputs.remove(id) ?: return

        // Remove children
        visibleInputs.indices.reversed().forEach { i ->
            val anEntry = visibleInputs[i]
            if (anEntry.parentEntry?.info?.id == id) {
                inputs.remove(anEntry.info?.id)
                visibleInputs.removeAt(i)
                notifyItemRemoved(i)
            }
        }

        val index = visibleInputs.indexOfFirst { it.info?.id == id }
        if (index != -1) {
            visibleInputs.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    private fun getPriorityForType(type: Int) = typePriorities[type] ?: Int.MAX_VALUE

    private fun setupDeviceTypePriorities(): MutableMap<Int, Int> {
        val priorities = Partner.get(mContext).inputsOrderMap.toMutableMap()
        var priority = priorities.size
        
        listOf(
            TYPE_BUNDLED_TUNER, TvInputInfo.TYPE_TUNER, -2, -4, -5, -6,
            TvInputInfo.TYPE_HDMI, TvInputInfo.TYPE_DVI, TvInputInfo.TYPE_COMPONENT,
            TvInputInfo.TYPE_SVIDEO, TvInputInfo.TYPE_COMPOSITE,
            TvInputInfo.TYPE_DISPLAY_PORT, TvInputInfo.TYPE_VGA,
            TvInputInfo.TYPE_SCART, TvInputInfo.TYPE_OTHER
        ).forEach { type ->
            if (!priorities.containsKey(type)) {
                priorities[type] = priority++
            }
        }
        return priorities
    }

    inner class InputViewHolder(v: View) : LauncherViewHolder(v) {
        private val bannerView = v as? BannerView
        private val imageView: ImageView? = v.findViewById(R.id.input_image)
        private val labelView: TextView? = v.findViewById(R.id.input_label)
        private val background: Drawable? = v.resources.getDrawable(R.drawable.input_banner_background, null)
        private val colorMatrix = ColorMatrix().apply { setScale(0.5f, 0.5f, 0.5f, 1f) }
        
        private var disconnected = false
        private var enabled = true

        fun init(entry: TvInputEntry) {
            itemView.visibility = View.VISIBLE
            val connected = entry.isConnected
            enabled = entry.isEnabled
            disconnected = entry.isDisconnected

            bannerView?.isEnabled = enabled

            imageView?.let { iv ->
                val drawable = entry.getImageDrawable(entry.state)
                bannerView?.background = if (drawable is BitmapDrawable && 
                    !drawable.bitmap.hasAlpha()) null else background
                bannerView?.viewDimmer?.setConcatMatrix(if (connected) null else colorMatrix)
                iv.setImageDrawable(drawable)
            }

            labelView?.let { lv ->
                lv.text = entry.label
                bannerView?.setTextViewColor(lv, 
                    if (entry.isLabelDarkColor) textColorDark else textColorLight)
            }

            setLaunchIntent(entry.launchIntent)
            setLaunchColor(ContextCompat.getColor(mContext, R.color.input_banner_launch_ripple_color))
        }

        override fun onClick(v: View) {
            when {
                disconnected -> {
                    Partner.get(mContext).disconnectedInputToastText?.takeIf { it.isNotEmpty() }?.let {
                        Toast.makeText(mContext, it, Toast.LENGTH_SHORT).show()
                    }
                }
                enabled -> super.onClick(v)
            }
        }
    }

    inner class TvInputEntry {
        val info: TvInputInfo?
        val type: Int
        val priority: Int
        val sortKey: Int
        val textColorOption: Int
        val parentEntry: TvInputEntry?
        val parentLabel: String?
        
        var label: String
        var banner: Drawable? = null
        var bannerState = 0
        var state = 0
        var numChildren = 0

        val isBundledTuner get() = type == TYPE_BUNDLED_TUNER
        val isEnabled get() = isConnected || !config.disableDisconnectedInputs
        val isConnected get() = isBundledTuner || state != TvInputManager.INPUT_STATE_DISCONNECTED
        val isDisconnected get() = !isBundledTuner && state == TvInputManager.INPUT_STATE_DISCONNECTED
        val isLabelDarkColor get() = textColorOption == 1

        constructor(label: String, banner: Drawable?, colorOption: Int, type: Int) {
            this.info = null
            this.label = label
            this.parentLabel = null
            this.banner = banner
            this.textColorOption = colorOption
            this.type = type
            this.priority = getPriorityForType(type)
            this.sortKey = Int.MAX_VALUE
            this.parentEntry = null
        }

        constructor(info: TvInputInfo, parent: TvInputEntry?, state: Int, ctx: Context) {
            this.info = info
            this.type = info.type
            this.state = state
            this.parentEntry = parent
            this.priority = getPriorityForType(-6)

            label = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) 
                info.loadCustomLabel(ctx) else null)?.toString()
                ?: info.loadLabel(ctx)?.toString() ?: ""

            textColorOption = info.serviceInfo.metaData?.getInt("input_banner_label_color_option", 0) ?: 0
            sortKey = info.serviceInfo.metaData?.getInt("input_sort_key", Int.MAX_VALUE) ?: Int.MAX_VALUE
            parentLabel = parent?.info?.loadLabel(ctx)?.toString() ?: label
            banner = getImageDrawable(state)
        }

        fun getImageDrawable(newState: Int): Drawable {
            if (banner != null && state == bannerState) return banner!!
            bannerState = newState

            info?.let {
                val icon = if (config.getStateIconFromTVInput && Build.VERSION.SDK_INT >= 24) {
                    null // API incompatibility
                } else {
                    it.loadIcon(mContext)
                }
                if (icon != null) {
                    banner = icon
                    return icon
                }
            }

            val drawableId = when (type) {
                TYPE_BUNDLED_TUNER, TvInputInfo.TYPE_TUNER -> when (state) {
                    TvInputManager.INPUT_STATE_DISCONNECTED -> R.drawable.ic_input_tuner_disconnected
                    TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> R.drawable.ic_input_tuner_standby
                    else -> R.drawable.ic_input_tuner
                }
                TvInputInfo.TYPE_HDMI -> when (state) {
                    TvInputManager.INPUT_STATE_DISCONNECTED -> R.drawable.ic_input_hdmi_disconnected
                    TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> R.drawable.ic_input_hdmi_standby
                    else -> R.drawable.ic_input_hdmi
                }
                // Add other types as needed...
                else -> when (state) {
                    TvInputManager.INPUT_STATE_DISCONNECTED -> R.drawable.ic_input_hdmi_disconnected
                    TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> R.drawable.ic_input_hdmi_standby
                    else -> R.drawable.ic_input_hdmi
                }
            }

            return ContextCompat.getDrawable(mContext, drawableId)!!.also { banner = it }
        }

        val launchIntent: Intent?
            get() = when {
                info != null -> if (info.isPassthroughInput) {
                    Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.id))
                } else {
                    Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(info.id))
                }
                isBundledTuner -> {
                    val uri = if (Build.VERSION.SDK_INT < 23) {
                        TvContract.buildChannelUri(0)
                    } else {
                        TvContract.Channels.CONTENT_URI
                    }
                    Intent(Intent.ACTION_VIEW, uri)
                }
                else -> null
            }

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is TvInputEntry) return false
            if (isBundledTuner && other.isBundledTuner) return true
            return info != null && other.info != null && info == other.info
        }

        override fun hashCode() = info?.hashCode() ?: type.hashCode()
    }

    private inner class InputsComparator : Comparator<TvInputEntry> {
        override fun compare(lhs: TvInputEntry?, rhs: TvInputEntry?): Int {
            if (rhs == null) return if (lhs != null) -1 else 0
            if (lhs == null) return 1

            if (config.disableDisconnectedInputs) {
                val disconnectedL = lhs.state == TvInputManager.INPUT_STATE_DISCONNECTED
                val disconnectedR = rhs.state == TvInputManager.INPUT_STATE_DISCONNECTED
                if (disconnectedL != disconnectedR) return if (disconnectedL) 1 else -1
            }

            if (lhs.priority != rhs.priority) return lhs.priority - rhs.priority

            if (lhs.type == TvInputInfo.TYPE_TUNER && rhs.type == TvInputInfo.TYPE_TUNER) {
                val lIsPhysical = isPhysicalTuner(mContext.packageManager, lhs.info!!)
                val rIsPhysical = isPhysicalTuner(mContext.packageManager, rhs.info!!)
                if (lIsPhysical != rIsPhysical) return if (lIsPhysical) -1 else 1
            }

            if (lhs.sortKey != rhs.sortKey) return rhs.sortKey - lhs.sortKey

            return if (TextUtils.equals(lhs.parentLabel, rhs.parentLabel) || lhs.parentLabel == null) {
                lhs.label.compareTo(rhs.label, ignoreCase = true)
            } else {
                lhs.parentLabel!!.compareTo(rhs.parentLabel!!, ignoreCase = true)
            }
        }
    }

    private inner class InputCallback : TvInputManager.TvInputCallback() {
        override fun onInputStateChanged(inputId: String, state: Int) {
            handler.sendMessage(handler.obtainMessage(MSG_STATE_UPDATED, state, 0, inputId))
        }
        override fun onInputAdded(inputId: String) {
            handler.sendMessage(handler.obtainMessage(MSG_INPUT_ADDED, inputId))
        }
        override fun onInputRemoved(inputId: String) {
            handler.sendMessage(handler.obtainMessage(MSG_INPUT_REMOVED, inputId))
        }
        override fun onTvInputInfoUpdated(inputInfo: TvInputInfo) {
            handler.sendMessage(handler.obtainMessage(MSG_INPUT_MODIFIED, inputInfo))
        }
    }

    private inner class InputsMessageHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_STATE_UPDATED -> inputStateUpdated(msg.obj as String, msg.arg1)
                MSG_INPUT_ADDED -> inputAdded(msg.obj as TvInputInfo?, false)
                MSG_INPUT_REMOVED -> inputRemoved(msg.obj as String)
                MSG_INPUT_MODIFIED -> { /* Handle if needed */ }
            }
        }
    }

    companion object {
        private const val MSG_STATE_UPDATED = 1
        private const val MSG_INPUT_ADDED = 2
        private const val MSG_INPUT_REMOVED = 3
        private const val MSG_INPUT_MODIFIED = 4
        
        private const val TYPE_BUNDLED_TUNER = -3
        
        private val PHYSICAL_TUNER_BLACKLIST = setOf(
            "com.google.android.videos",
            "com.google.android.youtube.tv",
            "com.amazon.avod",
            "com.amazon.hedwig"
        )

        private fun isPhysicalTuner(pm: PackageManager, input: TvInputInfo): Boolean {
            if (input.serviceInfo.packageName in PHYSICAL_TUNER_BLACKLIST) return false
            if (input.createSetupIntent() == null) return false

            val hasEpgPermission = pm.checkPermission(
                "com.android.providers.tv.permission.ACCESS_ALL_EPG_DATA",
                input.serviceInfo.packageName
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasEpgPermission) {
                return try {
                    val appInfo = pm.getApplicationInfo(input.serviceInfo.packageName, 0)
                    (appInfo.flags and 129) != 0
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
            }
            return true
        }
    }
}