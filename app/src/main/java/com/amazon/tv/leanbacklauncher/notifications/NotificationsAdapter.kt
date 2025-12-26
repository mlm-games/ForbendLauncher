package com.amazon.tv.leanbacklauncher.notifications

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.amazon.tv.leanbacklauncher.LauncherViewHolder
import com.amazon.tv.leanbacklauncher.MainActivity.IdleListener
import com.amazon.tv.leanbacklauncher.OpaqueBitmapTransformation
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.apps.AppsAdapter.ActionOpenLaunchPointListener
import com.amazon.tv.leanbacklauncher.capabilities.LauncherConfiguration
import com.amazon.tv.leanbacklauncher.core.LaunchException
import com.amazon.tv.leanbacklauncher.util.Util
import com.amazon.tv.tvrecommendations.IRecommendationsService
import com.amazon.tv.tvrecommendations.TvRecommendation
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue

open class NotificationsAdapter(context: Context, override val isPartnerClient: Boolean = false) :
    NotificationsServiceAdapter<NotificationsAdapter.NotifViewHolder>(context, 300000, 600000),
    IdleListener, ActionOpenLaunchPointListener {

    private val inflater = LayoutInflater.from(context)
    private val impressionDelay = context.resources.getInteger(R.integer.impression_delay)
    private val richRecommendationViewSupported = LauncherConfiguration.instance?.isRichRecommendationViewEnabled == true
    private val legacyRecommendationLayoutSupported = LauncherConfiguration.instance?.isLegacyRecommendationLayoutEnabled == true

    private val glideRequestManager = Glide.with(context)
    private val glideOptions = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        .transform(OpaqueBitmapTransformation(context, 
            ContextCompat.getColor(context, R.color.notif_background_color)))

    private val cardUpdateController = CardUpdateController()
    private val impressionHandler = ImpressionHandler(this)
    private val recommendationsHandler = RecommendationsHandler(this)
    
    private var isIdle = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onActionOpenLaunchPoint(component: String?, group: String?) {
        super.serviceOnActionOpenLaunchPoint(component, group)
    }

    override fun serviceStatusChanged(isReady: Boolean) {
        super.serviceStatusChanged(isReady)
        cardUpdateController.onServiceStatusChanged(isReady)
    }

    override fun onServiceDisconnected() {
        super.onServiceDisconnected()
        cardUpdateController.onDisconnected()
    }

    override fun onStopUi() {
        cardUpdateController.onDisconnected()
        super.onStopUi()
    }

    override fun getItemViewType(position: Int): Int {
        val rec = getRecommendation(position)
        return if (rec?.packageName == "android") VIEW_TYPE_CAPTIVE_PORTAL else VIEW_TYPE_RECOMMENDATION
    }

//    override fun getNonNotifItemCount() = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotifViewHolder {
        val view = when (viewType) {
            VIEW_TYPE_RECOMMENDATION -> createRecommendationCardView(parent)
            VIEW_TYPE_CAPTIVE_PORTAL -> CaptivePortalNotificationCardView(parent.context)
            else -> {
                Log.e(TAG, "Invalid view type = $viewType")
                return NotifViewHolder(View(parent.context))
            }
        }
        return NotifViewHolder(view)
    }

    private fun createRecommendationCardView(parent: ViewGroup): View {
        check(richRecommendationViewSupported) { "Unsupported device capabilities" }
        return if (legacyRecommendationLayoutSupported) {
            inflater.inflate(R.layout.notification_card, parent, false)
        } else {
            RecCardView(parent.context)
        }
    }


    override fun onBindViewHolder(holder: NotifViewHolder, position: Int) {
        if (position >= itemCount) return
        getRecommendation(position)?.let { holder.init(it) }
    }

    override fun onViewAttachedToWindow(holder: NotifViewHolder) {
        super.onViewAttachedToWindow(holder)
        impressionHandler.sendMessageDelayed(
            Message.obtain().apply { what = MSG_IMPRESSION; obj = holder },
            impressionDelay.toLong()
        )
        cardUpdateController.onViewAttachedToWindow(holder)
    }

    override fun onViewDetachedFromWindow(holder: NotifViewHolder) {
        super.onViewDetachedFromWindow(holder)
        impressionHandler.removeMessages(MSG_IMPRESSION, holder)
        cardUpdateController.onViewDetachedFromWindow(holder)
        holder.itemView.clearAnimation()
    }


    override fun onNotificationClick(intent: PendingIntent?, group: String?) {
        intent?.let { super.onActionRecommendationClick(it, group) }
    }

    override fun onIdleStateChange(isIdle: Boolean) {
        this.isIdle = isIdle
        super.onIdleStateChange(isIdle)
    }

    override fun onVisibilityChange(isVisible: Boolean) {
        isIdle = !isVisible
        super.onVisibilityChange(isVisible)
    }

    inner class NotifViewHolder(v: View) : LauncherViewHolder(v) {
        private val recView = v as? RecView
        private val useGlide = recView != null && recView !is CaptivePortalNotificationCardView
        
        var recommendation: TvRecommendation? = null
            private set
        var queuedState = QUEUE_STATE_NONE

        private var imageJob: Job? = null

        fun init(rec: TvRecommendation) {
            itemView.visibility = View.VISIBLE
            val refreshSameContent = NotificationUtils.equals(rec, recommendation)
            
            when (recView) {
                is CaptivePortalNotificationCardView -> recView.setRecommendation(rec, !refreshSameContent)
                is RecCardView -> recView.setRecommendation(rec, !refreshSameContent)
            }
            
            recommendation = rec
            setLaunchColor(recView?.getLaunchAnimationColor() ?: 0)
            queuedState = QUEUE_STATE_NONE

            if (useGlide) {
                recView?.setUseBackground(false)
                recView?.onStartImageFetch()
                glideRequestManager
                    .asBitmap()
                    .load(RecImageKey(rec))
                    .apply(glideOptions)
                    .into(recView!!)
            } else {
                recView?.setUseBackground(true)
                rec.contentImage?.let {
                    recView?.setMainImage(BitmapDrawable(mContext.resources, it))
                }
                if (!cardUpdateController.queueImageFetchIfDisconnected(this)) {
                    executeImageTask()
                }
            }
        }

        fun executeImageTask() {
            if (useGlide) return
            
            imageJob?.cancel()
            imageJob = scope.launch {
                val key = recommendation?.key ?: return@launch
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        mBoundService?.getImageForRecommendation(key)
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception while fetching card image", e)
                        null
                    }
                }
                bitmap?.let {
                    recView?.setMainImage(BitmapDrawable(mContext.resources, it))
                }
            }
        }

        fun getPendingIntent(): PendingIntent? = recommendation?.contentIntent
        fun getGroup(): String? = recommendation?.group

        override fun performLaunch() {
            val intent = getPendingIntent() 
                ?: throw LaunchException("No notification intent: $recommendation")
            
            try {
                Util.startActivity(mContext, intent)
                onLaunchSucceeded()
                onNotificationClick(intent)
            } catch (t: Throwable) {
                throw LaunchException("Could not launch notification intent", t)
            }
        }

        private fun onNotificationClick(intent: PendingIntent) {
            this@NotificationsAdapter.onNotificationClick(intent, getGroup())
            if (recommendation?.isAutoDismiss == true) {
                dismissNotification(recommendation!!)
            }
        }
    }

    private class CardUpdateController {
        private var isConnected = false
        private val waitingQueue = ConcurrentLinkedQueue<NotifViewHolder>()

        @Synchronized
        fun onDisconnected() { isConnected = false }

        @Synchronized
        fun onServiceStatusChanged(isReady: Boolean) {
            isConnected = isReady
            if (isReady) {
                while (waitingQueue.isNotEmpty()) {
                    val holder = waitingQueue.poll() ?: continue
                    if (holder.queuedState == QUEUE_STATE_WAITING) {
                        holder.executeImageTask()
                        holder.queuedState = QUEUE_STATE_NONE
                    } else {
                        holder.queuedState = QUEUE_STATE_PENDING
                    }
                }
            }
        }

        @Synchronized
        fun queueImageFetchIfDisconnected(holder: NotifViewHolder): Boolean {
            if (isConnected) return false
            if (holder.queuedState == QUEUE_STATE_NONE) {
                waitingQueue.add(holder)
                holder.queuedState = QUEUE_STATE_WAITING
            }
            return true
        }

        @Synchronized
        fun onViewAttachedToWindow(holder: NotifViewHolder) {
            if (isConnected && holder.queuedState == QUEUE_STATE_PENDING) {
                holder.executeImageTask()
                holder.queuedState = QUEUE_STATE_NONE
            } else if (!isConnected && holder.queuedState == QUEUE_STATE_DETACHED) {
                holder.queuedState = QUEUE_STATE_WAITING
            }
        }

        @Synchronized
        fun onViewDetachedFromWindow(holder: NotifViewHolder) {
            if (!isConnected && holder.queuedState == QUEUE_STATE_WAITING) {
                holder.queuedState = QUEUE_STATE_DETACHED
            }
        }
    }

    private class ImpressionHandler(adapter: NotificationsAdapter) : Handler(Looper.getMainLooper()) {
        private val adapterRef = WeakReference(adapter)
        
        override fun handleMessage(msg: Message) {
            val adapter = adapterRef.get() ?: return
            if (msg.what == MSG_IMPRESSION && !adapter.isIdle) {
                val holder = msg.obj as? NotifViewHolder ?: return
                holder.getPendingIntent()?.let { intent ->
                    adapter.onActionRecommendationImpression(intent, holder.getGroup())
                }
            }
        }
    }

    private class RecommendationsHandler(adapter: NotificationsAdapter) : Handler(Looper.getMainLooper()) {
        private val adapterRef = WeakReference(adapter)
        
        override fun handleMessage(msg: Message) {
            // Handle recommendations messages if needed
        }
    }

    companion object {
        private const val TAG = "NotificationsAdapter"
        private const val VIEW_TYPE_RECOMMENDATION = 0
        private const val VIEW_TYPE_CAPTIVE_PORTAL = 2
        private const val MSG_IMPRESSION = 11

        private const val QUEUE_STATE_NONE = 0
        private const val QUEUE_STATE_WAITING = 1
        private const val QUEUE_STATE_DETACHED = 2
        private const val QUEUE_STATE_PENDING = 3
    }
}