package com.amazon.tv.leanbacklauncher.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.TypedArray
import android.graphics.*
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.text.TextUtils
import android.util.Log
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.util.Util
import java.lang.ref.WeakReference

class NowPlayCardListener(
    private val context: Context,
    private val isTestRunning: Boolean = false
) : MediaSessionManager.OnActiveSessionsChangedListener {

    interface Listener {
        fun onClientChanged(clearing: Boolean)
        fun onMediaDataUpdated(mediaData: NowPlayingCardData)
        fun onClientPlaybackStateUpdate(state: Int, stateChangeTimeMs: Long, currentPosMs: Long)
    }

    private val cardMaxWidth = context.resources.getDimensionPixelOffset(R.dimen.notif_card_img_max_width)
    private val cardMaxHeight = context.resources.getDimensionPixelOffset(R.dimen.notif_card_img_height)
    private val bannerMaxWidth = context.resources.getDimensionPixelOffset(R.dimen.banner_width)
    private val bannerMaxHeight = context.resources.getDimensionPixelOffset(R.dimen.banner_height)
    private val nowPlayingDefaultDarkening = context.resources.getFraction(R.fraction.now_playing_icon_color_darkening, 1, 1)

    private var lastMediaController: MediaController? = null
    private var nowPlayCardListener: Listener? = null
    
    private val mediaSessionCallback = MediaControllerCallback(this)

    private class MediaControllerCallback(listener: NowPlayCardListener) : MediaController.Callback() {
        private val listenerRef = WeakReference(listener)

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val listener = listenerRef.get() ?: return
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPlaybackStateChanged: $state")
            }
            state?.let { listener.updatePlayback(it) }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            listenerRef.get()?.updateMetadata(metadata)
        }
    }

    override fun onActiveSessionsChanged(controllers: List<MediaController>?) {
        updateMediaSessionCallback(controllers?.firstOrNull())
    }

    private fun updateMediaSessionCallback(activeController: MediaController?) {
        if (activeController != lastMediaController) {
            lastMediaController?.unregisterCallback(mediaSessionCallback)
            activeController?.registerCallback(mediaSessionCallback)
            lastMediaController = activeController
        }

        val clearing = lastMediaController == null
        nowPlayCardListener?.onClientChanged(clearing)

        if (!clearing) {
            updateMetadata(lastMediaController?.metadata)
            updatePlayback(lastMediaController?.playbackState)
        }
    }

    @Synchronized
    fun setRemoteControlListener(listener: Listener?) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "setRemoteControlListener: $listener")
        }

        val manager = context.applicationContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        
        if (listener != null) {
            manager.addOnActiveSessionsChangedListener(this, null)
            nowPlayCardListener = listener
            checkForMediaSession()
        } else {
            manager.removeOnActiveSessionsChangedListener(this)
            nowPlayCardListener = null
            updateMediaSessionCallback(null)
        }
    }

    private fun updateMetadata(metadata: MediaMetadata?) {
        val listener = nowPlayCardListener ?: return
        metadata ?: return

        val manager = context.applicationContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        
        val data = NowPlayingCardData().apply {
            setPendingIntentAndPackage(this, manager)
            title = getMetadataString(metadata, MediaMetadata.METADATA_KEY_TITLE, context.getString(R.string.unknown_title))
            
            val fallbackArtist = getApplicationLabel(playerPackage) ?: context.getString(R.string.unknown_artist)
            artist = getMetadataString(metadata, MediaMetadata.METADATA_KEY_ARTIST, fallbackArtist)
            albumArtist = getMetadataString(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST, context.getString(R.string.unknown_album_artist))
            albumTitle = getMetadataString(metadata, MediaMetadata.METADATA_KEY_ALBUM, context.getString(R.string.unknown_album))
            year = getMetadataLong(metadata, MediaMetadata.METADATA_KEY_YEAR, -1)
            trackNumber = getMetadataLong(metadata, MediaMetadata.METADATA_KEY_TRACK_NUMBER, -1)
            duration = getMetadataLong(metadata, MediaMetadata.METADATA_KEY_DURATION, -1)
            artwork = getResizedBitmap(getArt(metadata), false)
            badgeIcon = getBadgeIcon(metadata)
            launchColor = getColor(playerPackage)
            
            if (artwork == null) {
                artwork = generateArtwork(playerPackage)
            }
        }
        
        listener.onMediaDataUpdated(data)
    }

    private fun updatePlayback(state: PlaybackState?) {
        val listener = nowPlayCardListener ?: return
        state ?: return
        listener.onClientPlaybackStateUpdate(state.state, state.lastPositionUpdateTime, state.position)
    }

    private fun getArt(metadata: MediaMetadata?): Bitmap? {
        metadata ?: return null
        return metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
    }

    private fun getBadgeIcon(metadata: MediaMetadata?): Bitmap? =
        metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    private fun getMetadataString(metadata: MediaMetadata?, key: String, default: String): String =
        metadata?.getString(key) ?: default

    private fun getMetadataLong(metadata: MediaMetadata?, key: String, default: Long): Long =
        metadata?.getLong(key)?.takeIf { it != 0L } ?: default

    private fun getApplicationLabel(packageName: String?): String? {
        packageName ?: return null
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun getResizedBitmap(image: Bitmap?, isBanner: Boolean, lowRes: Boolean = false): Bitmap? {
        val factor = if (lowRes) 0.1f else 1f
        val maxW = if (isBanner) bannerMaxWidth else (cardMaxWidth * factor).toInt()
        val maxH = if (isBanner) bannerMaxHeight else (cardMaxHeight * factor).toInt()
        return Util.getSizeCappedBitmap(image, maxW, maxH)
    }

    private fun getColor(packageName: String?): Int {
        val defaultColor = context.resources.getColor(R.color.notif_background_color, null)
        if (packageName.isNullOrEmpty()) return defaultColor

        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName) ?: return defaultColor
        val info = pm.resolveActivity(intent, 0) ?: return defaultColor

        return try {
            val theme = context.createPackageContext(packageName, 0).theme
            theme.applyStyle(info.activityInfo.themeResource, true)
            val a: TypedArray = theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorPrimary))
            val color = a.getColor(0, defaultColor)
            a.recycle()
            color
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Exception", e)
            defaultColor
        }
    }

    private fun setPendingIntentAndPackage(data: NowPlayingCardData, sessionManager: MediaSessionManager) {
        data.clickIntent = null
        data.playerPackage = null

        val controller = sessionManager.getActiveSessions(null).firstOrNull { 
            (it.flags.toInt() and 1) != 0
        } ?: return

        data.playerPackage = controller.packageName
        data.clickIntent = controller.sessionActivity ?: getPendingIntentFallback(controller.packageName)
    }

    private fun getPendingIntentFallback(packageName: String): PendingIntent? {
        val lbIntent = context.packageManager.getLeanbackLaunchIntentForPackage(packageName) ?: return null
        lbIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        lbIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(context, 0, lbIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun generateArtwork(playerPackage: String?): Bitmap {
        val appColor = getColor(playerPackage)
        val color = Color.rgb(
            (Color.red(appColor) * nowPlayingDefaultDarkening).toInt(),
            (Color.green(appColor) * nowPlayingDefaultDarkening).toInt(),
            (Color.blue(appColor) * nowPlayingDefaultDarkening).toInt()
        )

        val playIcon = context.resources.getDrawable(R.drawable.ic_now_playing_default, null)
        val (width, height) = playIcon.intrinsicWidth to playIcon.intrinsicHeight
        
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            Canvas(this).also { canvas ->
                playIcon.setBounds(Rect(0, 0, width, height))
                playIcon.draw(canvas)
            }
        }
    }

    fun forceUpdate() {
        lastMediaController?.let {
            updateMetadata(it.metadata)
            updatePlayback(it.playbackState)
        }
    }

    fun checkForMediaSession() {
        val manager = context.applicationContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        manager?.let {
            if (!isTestRunning) {
                android.media.session.MediaSession(context.applicationContext, "NowPlayCardListener").release()
            }
            onActiveSessionsChanged(it.getActiveSessions(null))
        }
    }

    companion object {
        private const val TAG = "NowPlayCardListener"
    }
}