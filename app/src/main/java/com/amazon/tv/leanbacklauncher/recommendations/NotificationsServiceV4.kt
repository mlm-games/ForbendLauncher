package com.amazon.tv.leanbacklauncher.recommendations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import com.amazon.tv.leanbacklauncher.BuildConfig
import com.amazon.tv.tvrecommendations.service.RankerParametersFactory
import com.amazon.tv.tvrecommendations.service.RecommendationsManager
import com.amazon.tv.tvrecommendations.service.RecommendationsManager.NotificationResolver
import com.amazon.tv.tvrecommendations.service.RecommendationsUtil.isCaptivePortal
import com.amazon.tv.tvrecommendations.service.RecommendationsUtil.isRecommendation

class NotificationsServiceV4 :
    BaseNotificationsService(false, GservicesRankerParameters.Factory()) {
    private val mDelegate: NotificationServiceDelegate? = null
    private val TAG by lazy { if (BuildConfig.DEBUG) ("[*]" + javaClass.simpleName).take(21) else javaClass.simpleName }

    interface NotificationServiceDelegate {
        fun onFetchExistingNotifications(statusBarNotificationArr: Array<StatusBarNotification>?)
        fun onNotificationPosted(statusBarNotification: StatusBarNotification?)
        fun onNotificationRemoved(statusBarNotification: StatusBarNotification?)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (BuildConfig.DEBUG) Log.d(TAG, "onListenerConnected()")
    }

    override fun onFetchExistingNotifications(notifications: Array<StatusBarNotification>) {
        if (isEnabled) {
            super.onFetchExistingNotifications(notifications)
            mDelegate?.onFetchExistingNotifications(notifications)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (isEnabled) {
            super.onNotificationPosted(sbn)
            mDelegate?.onNotificationPosted(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (isEnabled) {
            super.onNotificationRemoved(sbn)
            mDelegate?.onNotificationRemoved(sbn)
        }
    }

    private val isEnabled: Boolean
        get() = true
}

abstract class BaseNotificationsService(
    private val mUnbundled: Boolean,
    private val mRankerParametersFactory: RankerParametersFactory?
) : NotificationListenerService(), NotificationResolver {
    private var mConnectivityManager: ConnectivityManager? = null
    private val mCurrentUser: UserHandle? = null
    private var mManager: RecommendationsManager? = null
    private var mReceiver: BroadcastReceiver? = null
    private val mTag: String

    init {
        this.mTag = if (mUnbundled) "UB-BaseNotifService" else "B-BaseNotifService"
    }

    override fun onCreate() {
        super.onCreate()
        // UserManager manager = (UserManager) getApplicationContext().getSystemService(Context.USER_SERVICE);
        // this.mCurrentUser = manager.getUserProfiles().get(0); // todo hacky
        val appContext = getApplicationContext()
        this.mConnectivityManager =
            appContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val filter = IntentFilter()
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE")
        this.mManager = RecommendationsManager.getInstance(
            this,
            this.mUnbundled,
            this.mRankerParametersFactory!!
        )
        this.mReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val info =
                    this@BaseNotificationsService.mConnectivityManager!!.getActiveNetworkInfo()
                if (info == null || !info.isConnected()) {
                    this@BaseNotificationsService.mManager!!.removeAllCaptivePortalNotifications()
                }
            }
        }
        appContext.registerReceiver(this.mReceiver, filter)
        this.mManager!!.setNotificationResolver(this)
        this.mManager!!.onCreate()
    }

    override fun onDestroy() {
        getApplicationContext().unregisterReceiver(this.mReceiver)
        this.mManager!!.onDestroy()
    }

    override fun cancelRecommendation(key: String) {
        super.cancelNotification(key)
    }

    override fun getNotification(key: String): StatusBarNotification? {
        if (TextUtils.isEmpty(key)) {
            return null
        }
        val keySet = arrayOf(key)
        var ret: Array<StatusBarNotification?>? = null
        if (this.mManager!!.isConnectedToNotificationService()) {
            try {
                ret = getActiveNotifications(keySet) // 0
            } catch (e: SecurityException) {
                Log.d(this.mTag, "Exception fetching notification", e)
            }
        } else {
            Log.e(this.mTag, "Image request with DISCONNECTED service, ignoring request.")
        }
        if (ret == null || ret.isEmpty()) {
            return null
        }
        return ret[0]
    }

    override fun fetchExistingNotifications() {
        val snotifications = getActiveNotifications()

        /* Get rid of any recommendations already ruined by Amazon's hand... */
        if (snotifications != null && snotifications.size > 0) for (sbn in snotifications) {
            val notification = sbn.getNotification()

            // if (BuildConfig.DEBUG) Log.d(this.mTag, "fetchExistingNotifications: get " + notification);

            // FIXME (issue with com.amazon.device.sale.service)
            if (notification.largeIcon == null) {
                cancelNotification(sbn.getKey())
                // if (BuildConfig.DEBUG) Log.d(this.mTag, "fetchExistingNotifications: cancel " + notification);
            }
        }

        onFetchExistingNotifications(getActiveNotifications()) // 1
    }

    protected open fun onFetchExistingNotifications(notifications: Array<StatusBarNotification>) {
        // if (BuildConfig.DEBUG) Log.d(this.mTag, " onFetchExistingNotifications +++ resetNotifications +++");
        this.mManager!!.resetNotifications()
        var i = 0
        for (sbn in notifications) {
            // && sbn.getUser().equals(this.mCurrentUser)
            if (isRecommendation(sbn)) {
                i++
                this.mManager!!.addNotification(sbn)
                // if (BuildConfig.DEBUG) Log.d(this.mTag, " onFetchExistingNotifications +++ isRecommendation: ADD +++ " + i);
            } else if (isCaptivePortal(getApplicationContext(), sbn)) {
                this.mManager!!.addCaptivePortalNotification(sbn)
            }
        }
    }

    override fun onListenerConnected() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestInterruptionFilter(4)
        } else if (Build.VERSION.SDK_INT >= 21) {
            requestListenerHints(1)
        }
        // todo setOnNotificationPostedTrim(1);
        this.mManager!!.sendConnectionStatus(true)
    }

    override fun onBind(intent: Intent): IBinder? {
        if ("android.service.notification.NotificationListenerService" == intent.getAction()) {
            return super.onBind(intent)
        }
        return null
    }

    override fun onUnbind(intent: Intent): Boolean {
        if ("android.service.notification.NotificationListenerService" == intent.getAction()) {
            this.mManager!!.sendConnectionStatus(false)
        }
        return super.onUnbind(intent)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // sbn.getUser().equals(this.mCurrentUser)
        if (isRecommendation(sbn)) {
            this.mManager!!.addNotification(sbn)
        }
        if (isCaptivePortal(getApplicationContext(), sbn)) {
            this.mManager!!.addCaptivePortalNotification(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // && sbn.getUser().equals(this.mCurrentUser)
        if (isRecommendation(sbn)) {
            this.mManager!!.removeNotification(sbn)
        }
    }
}