package com.amazon.tv.tvrecommendations.service

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import com.amazon.tv.tvrecommendations.TvRecommendation
import kotlin.math.min

object RecommendationsUtil {

    fun isRecommendation(sbn: StatusBarNotification?): Boolean =
        sbn?.notification?.category == "recommendation"

    fun isInPartnerRow(context: Context, sbn: StatusBarNotification): Boolean =
        sbn.notification.group == "partner_row_entry"

    fun isCaptivePortal(context: Context, sbn: StatusBarNotification): Boolean {
        val tag = sbn.tag
        if (tag == "CaptivePortal.Notification") return true
        
        if (tag == "Connectivity.Notification" || tag?.startsWith("ConnectivityNotification:") == true) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return cm.allNetworks.any { network ->
                cm.getNetworkInfo(network)?.isConnected == true &&
                cm.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
            }
        }
        return false
    }

    fun equals(left: StatusBarNotification?, right: StatusBarNotification?): Boolean {
        if (left == null || right == null) return left === right
        return TextUtils.equals(left.packageName, right.packageName) &&
               left.id == right.id &&
               TextUtils.equals(left.tag, right.tag)
    }

    fun getSizeCappedBitmap(image: Bitmap?, maxWidth: Int, maxHeight: Int): Bitmap? {
        image ?: return null
        val (imgWidth, imgHeight) = image.width to image.height
        
        if ((imgWidth <= maxWidth && imgHeight <= maxHeight) || imgWidth <= 0 || imgHeight <= 0) {
            return image
        }

        val scale = min(1f, maxHeight.toFloat() / imgHeight)
        if (scale >= 1f && imgWidth <= maxWidth) return image

        val deltaW = maxOf((imgWidth * scale).toInt() - maxWidth, 0) / scale
        val matrix = Matrix().apply { postScale(scale, scale) }
        
        return runCatching {
            Bitmap.createBitmap(
                image,
                (deltaW / 2).toInt(),
                0,
                (imgWidth - deltaW).toInt(),
                imgHeight,
                matrix,
                true
            )
        }.getOrNull() ?: image
    }

    fun fromStatusBarNotification(context: Context, sbn: StatusBarNotification): TvRecommendation? {
        val notification = sbn.notification
        val extras = notification.extras ?: return null

        val largeBitmap = notification.largeIcon
//            ?: (extras.getParcelable<BitmapDrawable>(Notification.EXTRA_LARGE_ICON))?.bitmap
//            ?: (extras.getParcelable<BitmapDrawable>(Notification.EXTRA_LARGE_ICON_BIG))?.bitmap

        val (progressMax, progress) = if (extras.containsKey(Notification.EXTRA_PROGRESS) && 
            !extras.containsKey(Notification.EXTRA_PROGRESS_INDETERMINATE)) {
            extras.getInt(Notification.EXTRA_PROGRESS_MAX) to extras.getInt(Notification.EXTRA_PROGRESS)
        } else 0 to 0

        val score = extras.getDouble("cached_score", -1.0)

        return TvRecommendation(
            packageName = sbn.packageName,
            key = sbn.key,
            postTime = sbn.postTime,
            group = notification.group,
            sortKey = notification.sortKey,
            contentIntent = notification.contentIntent,
            isAutoDismiss = false,
            width = extras.getInt("notif_img_width", -1),
            height = extras.getInt("notif_img_height", -1),
            color = notification.color,
            contentImage = largeBitmap,
            backgroundImageUri = extras.getString(Notification.EXTRA_BACKGROUND_IMAGE_URI),
            title = extras.getCharSequence(Notification.EXTRA_TITLE),
            text = extras.getCharSequence(Notification.EXTRA_TEXT),
            sourceName = extras.getCharSequence(Notification.EXTRA_INFO_TEXT),
            badgeIcon = notification.icon,
            progressMax = progressMax,
            progress = progress,
            score = score,
            replacedPackageName = extras.getString("com.google.android.leanbacklauncher.replacespackage")
        )
    }
}