package com.amazon.tv.leanbacklauncher.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.TextView
import android.widget.ViewFlipper
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.apps.LaunchPoint
import com.amazon.tv.leanbacklauncher.core.LaunchException
import com.amazon.tv.leanbacklauncher.util.Util

class HomeScreenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewFlipper(context, attrs) {

    private lateinit var errorMessageText: TextView
    private lateinit var row: NotificationRowView
    private var notifRowViewIndex = 0
    private var preparingViewIndex = 0
    private var timeoutViewIndex = 0
    private var hasNowPlayingCard = false

    val homeScreenMessaging = HomeScreenMessaging(this)
    val notificationRow: NotificationRowView get() = row
    val isRowViewVisible: Boolean get() = displayedChild == notifRowViewIndex

    override fun onFinishInflate() {
        super.onFinishInflate()
        notifRowViewIndex = indexOfChild(findViewById(R.id.list))
        preparingViewIndex = indexOfChild(findViewById(R.id.notification_preparing))
        timeoutViewIndex = indexOfChild(findViewById(R.id.notification_timeout))
        errorMessageText = findViewById(R.id.text_error_message)
        row = findViewById(R.id.list)
        homeScreenMessaging.resetToPreparing()
    }

    fun flipToTimeout() {
        displayedChild = timeoutViewIndex
    }

    fun flipToPreparing() {
        // Optional: implement if needed
    }

    fun flipToNotifications() {
        displayedChild = notifRowViewIndex
    }

    fun flipToView(view: Int) {
        if (hasNowPlayingCard) {
            hasNowPlayingCard = false
        }
        when (view) {
            0 -> flipToNotifications()
            1 -> flipToPreparing()
            2 -> {
                flipToTimeout()
                errorMessageText.setText(R.string.recommendation_row_empty_message_recs_disabled)
            }
            3 -> {
                flipToTimeout()
                errorMessageText.setText(R.string.recommendation_row_empty_message_no_recs)
            }
            4 -> {
                flipToTimeout()
                errorMessageText.setText(R.string.recommendation_row_empty_message_no_connection)
            }
        }
    }

    fun onClientChanged(clearing: Boolean) {
        // Handle client change if needed
    }

    fun onClientPlaybackStateUpdate(state: Int, stateChangeTimeMs: Long, currentPosMs: Long) {
        // Handle playback state update if needed
    }

    fun getPendingIntent(): PendingIntent? = null

    protected fun performLaunch() {
        val intent = getPendingIntent() 
            ?: throw LaunchException("No pending intent")
        
        try {
            Util.startActivity(context, intent)
        } catch (t: Throwable) {
            throw LaunchException("Could not launch notification intent", t)
        }
    }

    fun isTranslucentTheme(): Boolean {
        val pendingIntent = getPendingIntent() ?: return false
        val packageName = pendingIntent.creatorPackage ?: return false
        
        val intent = Intent().apply {
            setPackage(packageName)
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val info = context.packageManager.resolveActivity(intent, 0)
        return info?.let { LaunchPoint.isTranslucentTheme(context, it) } ?: false
    }
}