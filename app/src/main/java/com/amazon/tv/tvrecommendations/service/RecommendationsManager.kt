package com.amazon.tv.tvrecommendations.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.RemoteCallbackList
import android.service.notification.StatusBarNotification
import android.util.Log
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.tvrecommendations.IRecommendationsClient
import com.amazon.tv.tvrecommendations.TvRecommendation

@SuppressLint("StaticFieldLeak")
class RecommendationsManager private constructor(
    private val context: Context,
    unbundled: Boolean,
    rankerParameters: RankerParameters
) : Ranker.RankingListener {

    private val tag = if (unbundled) "UB-RecommendationsManager" else "B-RecommendationsManager"
    private val cardMaxWidth = context.resources.getDimensionPixelOffset(R.dimen.notif_card_img_max_width)
    private val cardMaxHeight = context.resources.getDimensionPixelOffset(R.dimen.notif_card_img_height)
    private val bannerMaxWidth = context.resources.getDimensionPixelOffset(R.dimen.banner_width)
    private val bannerMaxHeight = context.resources.getDimensionPixelOffset(R.dimen.banner_height)
    private val maxRecsPerApp = context.resources.getInteger(R.integer.max_recommendations_per_app)

    private val dbHelper = DbHelper.getInstance(context)
    private val ranker = Ranker(context, dbHelper, rankerParameters).apply { addListener(this@RecommendationsManager) }
    private val appListener = ServiceAppListener(context, ranker)
    private val clientHandler = ClientHandler()

    private val packageToRecSet = hashMapOf<String, ArrayList<StatusBarNotification>>()
    private val partnerList = arrayListOf<StatusBarNotification>()
    
    private var notificationResolver: NotificationResolver? = null
    private var connectedToNotificationService = false
    private var rankerReady = false
    private var started = false

    interface NotificationResolver {
        fun cancelRecommendation(key: String)
        fun fetchExistingNotifications()
        fun getNotification(key: String): StatusBarNotification?
    }

    private sealed class RecOperation(val notification: StatusBarNotification) {
        class Add(notification: StatusBarNotification) : RecOperation(notification)
        class Change(notification: StatusBarNotification) : RecOperation(notification)
        class Remove(notification: StatusBarNotification) : RecOperation(notification)
    }

    @SuppressLint("HandlerLeak")
    private inner class ClientHandler : Handler(Looper.getMainLooper()) {
        val clients = RemoteCallbackList<IRecommendationsClient>()
        val partnerClients = RemoteCallbackList<IRecommendationsClient>()
        
        private val captivePortalPosted = mutableListOf<StatusBarNotification>()
        private val captivePortalShowing = mutableListOf<StatusBarNotification>()
        private val captivePortalRemoved = mutableListOf<StatusBarNotification>()
        private val recBatch = mutableListOf<RecOperation>()

        fun registerClient(client: IRecommendationsClient, isPartner: Boolean) {
            if (isPartner) partnerClients.register(client) else clients.register(client)
        }

        fun unregisterClient(client: IRecommendationsClient?, isPartner: Boolean) {
            client ?: return
            if (isPartner) partnerClients.unregister(client) else clients.unregister(client)
        }

        fun getClientCount() = clients.registeredCallbackCount

        fun enqueueStartIfReady() { removeMessages(RecommendationFlags.MSG_START); sendEmptyMessage(
            RecommendationFlags.MSG_START) }
        
        fun enqueueNotificationReset() {
            removeMessages(RecommendationFlags.MSG_NOTIFICATION)
            removeMessages(RecommendationFlags.MSG_CAPTIVE_PORTAL_POSTED)
            removeMessages(RecommendationFlags.MSG_CAPTIVE_PORTAL_REMOVED)
            recBatch.clear()
            captivePortalPosted.clear()
            captivePortalShowing.clear()
            captivePortalRemoved.clear()
            removeMessages(RecommendationFlags.MSG_NOTIFICATION_RESET)
            sendEmptyMessageDelayed(RecommendationFlags.MSG_NOTIFICATION_RESET, 100)
        }

        @Synchronized
        fun enqueueNotificationPosted(sbn: StatusBarNotification) {
            recBatch.add(RecOperation.Add(sbn))
            removeMessages(RecommendationFlags.MSG_NOTIFICATION)
            sendEmptyMessageDelayed(RecommendationFlags.MSG_NOTIFICATION, 100)
        }

        @Synchronized
        fun enqueueNotificationRemoved(sbn: StatusBarNotification) {
            recBatch.add(RecOperation.Remove(sbn))
            removeMessages(RecommendationFlags.MSG_NOTIFICATION)
            sendEmptyMessageDelayed(RecommendationFlags.MSG_NOTIFICATION, 100)
        }

        fun enqueueConnectionStatus(connected: Boolean) {
            sendMessage(obtainMessage(RecommendationFlags.MSG_CONNECTION_STATUS, if (connected) 1 else 0, 0))
        }

        @Synchronized
        fun enqueueCaptivePortalPosted(sbn: StatusBarNotification) {
            if (sbn !in captivePortalPosted && sbn !in captivePortalShowing) {
                if (captivePortalShowing.isNotEmpty()) enqueueAllCaptivePortalRemoved()
                captivePortalPosted.clear()
                captivePortalPosted.add(sbn)
                removeMessages(RecommendationFlags.MSG_CAPTIVE_PORTAL_POSTED)
                sendEmptyMessageDelayed(RecommendationFlags.MSG_CAPTIVE_PORTAL_POSTED, 100)
            }
        }

        @Synchronized
        fun enqueueAllCaptivePortalRemoved() {
            if (captivePortalShowing.isNotEmpty()) {
                synchronized(captivePortalRemoved) { captivePortalRemoved.addAll(captivePortalShowing) }
                removeMessages(RecommendationFlags.MSG_CAPTIVE_PORTAL_REMOVED)
                sendEmptyMessageDelayed(RecommendationFlags.MSG_CAPTIVE_PORTAL_REMOVED, 100)
            }
        }

        @Synchronized
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                RecommendationFlags.MSG_START -> startIfReady()
                RecommendationFlags.MSG_NOTIFICATION -> {
                    recommendationBatchPosted(recBatch.toList())
                    recBatch.clear()
                }
                RecommendationFlags.MSG_NOTIFICATION_RESET -> {
                    onRecommendationsReset(recBatch.toList())
                    recommendationBatchPosted(recBatch.toList())
                    recBatch.clear()
                }
                RecommendationFlags.MSG_CONNECTION_STATUS -> setConnectedToNotificationService(msg.arg1 == 1)
                RecommendationFlags.MSG_CAPTIVE_PORTAL_POSTED -> {
                    val ops = captivePortalPosted.map { RecOperation.Add(it) }
                    recommendationBatchPosted(ops)
                    captivePortalShowing.addAll(captivePortalPosted)
                    captivePortalPosted.clear()
                }
                RecommendationFlags.MSG_CAPTIVE_PORTAL_REMOVED -> {
                    val ops = captivePortalRemoved.map { RecOperation.Remove(it) }
                    recommendationBatchPosted(ops)
                    captivePortalShowing.removeAll(captivePortalRemoved.toSet())
                    captivePortalRemoved.clear()
                }
            }
        }
    }

    fun setNotificationResolver(resolver: NotificationResolver) { notificationResolver = resolver }
    fun onCreate() { appListener.onCreate() }
    fun onDestroy() { appListener.onDestroy() }
    fun isConnectedToNotificationService() = connectedToNotificationService

    override fun onRankerReady() {
        rankerReady = true
        clientHandler.enqueueStartIfReady()
    }

    fun onActionOpenLaunchPoint(key: String, group: String?) = ranker.onActionOpenLaunchPoint(key, group ?: "")
    fun onActionOpenRecommendation(key: String, group: String?) = ranker.onActionOpenRecommendation(key, group ?: "")
    fun onActionRecommendationImpression(key: String, group: String?) = ranker.onActionRecommendationImpression(key, group ?: "")

    fun registerNotificationsClient(client: IRecommendationsClient, isPartner: Boolean) {
        clientHandler.registerClient(client, isPartner)
        clientHandler.enqueueStartIfReady()
    }

    fun unregisterNotificationsClient(client: IRecommendationsClient?, isPartner: Boolean) {
        clientHandler.unregisterClient(client, isPartner)
    }

    private fun startIfReady() {
        if (rankerReady && connectedToNotificationService && clientHandler.getClientCount() != 0) {
            started = true
            packageToRecSet.clear()
            partnerList.clear()
            notifyServiceStatusChange(true)
            notificationResolver?.fetchExistingNotifications()
        }
    }

    private fun setConnectedToNotificationService(connected: Boolean) {
        connectedToNotificationService = connected
        if (connected) {
            startIfReady()
        } else if (started) {
            clearAllRecommendations(3)
            notifyServiceStatusChange(false)
            started = false
        }
    }

    private fun notifyServiceStatusChange(isReady: Boolean) {
        broadcastToClients(clientHandler.clients) { it.onServiceStatusChanged(isReady) }
        broadcastToClients(clientHandler.partnerClients) { it.onServiceStatusChanged(isReady) }
    }

    private fun clearAllRecommendations(reason: Int) {
        broadcastToClients(clientHandler.clients) { it.onClearRecommendations(reason) }
        broadcastToClients(clientHandler.partnerClients) { it.onClearRecommendations(reason) }
    }

    private inline fun broadcastToClients(
        clients: RemoteCallbackList<IRecommendationsClient>,
        action: (IRecommendationsClient) -> Unit
    ) {
        val count = clients.beginBroadcast()
        try {
            repeat(count) { i ->
                runCatching { action(clients.getBroadcastItem(i)) }
                    .onFailure { Log.e(tag, "RemoteException", it) }
            }
        } finally {
            clients.finishBroadcast()
        }
    }

    private fun onRecommendationsReset(notifications: List<RecOperation>) {
        if (!started) return

        var totalRecs = 0
        var blacklistedRecs = 0

        notifications.forEach { op ->
            if (!RecommendationsUtil.isInPartnerRow(context, op.notification)) {
                when (op) {
                    is RecOperation.Add -> {
                        totalRecs++
                        if (ranker.isBlacklisted(op.notification.packageName)) blacklistedRecs++
                    }
                    is RecOperation.Remove -> {
                        totalRecs--
                        if (ranker.isBlacklisted(op.notification.packageName)) blacklistedRecs--
                    }
                    else -> {}
                }
            }
        }

        val reason = when {
            totalRecs == 0 -> if (ranker.hasBlacklistedPackages()) 4 else 3
            totalRecs == blacklistedRecs -> 2
            else -> 3
        }
        clearAllRecommendations(reason)
    }

    private fun recommendationBatchPosted(batch: List<RecOperation>) {
        if (!started) return

        val changes = mutableListOf<RecOperation>()

        batch.forEach { op ->
            val sbn = op.notification
            val inPartnerRow = RecommendationsUtil.isInPartnerRow(context, sbn)

            when (op) {
                is RecOperation.Add -> {
                    if (inPartnerRow) handlePartnerRecAdded(sbn)
                    else scoreAndInsertRec(sbn, changes)
                }
                is RecOperation.Remove -> {
                    if (inPartnerRow) handlePartnerRecRemoved(sbn)
                    else {
                        packageToRecSet[sbn.packageName]?.let { recSet ->
                            recSet.removeAll { RecommendationsUtil.equals(it, sbn) }
                        }
                        changes.add(RecOperation.Remove(sbn))
                    }
                }
                else -> {}
            }
        }

        postChangesToClients(changes)
    }

    private fun scoreAndInsertRec(sbn: StatusBarNotification, changes: MutableList<RecOperation>) {
        ranker.prepNormalizationValues()
        val pkg = sbn.packageName
        ranker.markPostedRecommendations(pkg)

        if (ranker.isBlacklisted(pkg)) return

        val recSet = packageToRecSet.getOrPut(pkg) { arrayListOf() }

        if (recSet.isEmpty()) {
            tidyRecommendation(sbn)
            recSet.add(sbn)
            ranker.calculateAdjustedScore(sbn, 0, RecommendationsUtil.isCaptivePortal(context, sbn))
            changes.add(RecOperation.Add(sbn))
            return
        }

        val comparator = Comparator<StatusBarNotification> { o1, o2 ->
            when {
                RecommendationsUtil.isCaptivePortal(context, o1) && !RecommendationsUtil.isCaptivePortal(context, o2) -> -1
                !RecommendationsUtil.isCaptivePortal(context, o1) && RecommendationsUtil.isCaptivePortal(context, o2) -> 1
                else -> ranker.getBaseNotificationScore(o1).compareTo(ranker.getBaseNotificationScore(o2))
            }
        }

        val existingIndex = recSet.indexOfFirst { RecommendationsUtil.equals(it, sbn) }
        val found = existingIndex >= 0

        if (found) {
            recSet.removeAt(existingIndex)
        } else if (maxRecsPerApp > 0 && recSet.size >= maxRecsPerApp) {
            notificationResolver?.cancelRecommendation(sbn.key)
            return
        } else {
            tidyRecommendation(sbn)
        }

        val insertPos = recSet.indexOfFirst { comparator.compare(it, sbn) < 0 }.let { if (it < 0) recSet.size else it }
        recSet.add(insertPos, sbn)

        if (!found) {
            ranker.calculateAdjustedScore(sbn, insertPos, RecommendationsUtil.isCaptivePortal(context, sbn))
            changes.add(RecOperation.Add(sbn))
        }

        // Recalculate scores for subsequent items
        for (i in insertPos until recSet.size) {
            val rec = recSet[i]
            ranker.calculateAdjustedScore(rec, i, RecommendationsUtil.isCaptivePortal(context, rec))
            changes.add(RecOperation.Change(rec))
        }
    }

    private fun handlePartnerRecAdded(sbn: StatusBarNotification) {
        val existing = partnerList.indexOfFirst { RecommendationsUtil.equals(it, sbn) }
        if (existing >= 0) {
            partnerList[existing] = sbn
        } else {
            partnerList.add(sbn)
        }
        tidyRecommendation(sbn)

        val rec = RecommendationsUtil.fromStatusBarNotification(context, sbn)
        broadcastToClients(clientHandler.partnerClients) {
            if (existing >= 0) it.onUpdateRecommendation(rec) else it.onAddRecommendation(rec)
        }
    }

    private fun handlePartnerRecRemoved(sbn: StatusBarNotification) {
        partnerList.removeAll { RecommendationsUtil.equals(it, sbn) }
        val rec = RecommendationsUtil.fromStatusBarNotification(context, sbn)
        broadcastToClients(clientHandler.partnerClients) { it.onRemoveRecommendation(rec) }
    }

    private fun postChangesToClients(changes: List<RecOperation>) {
        if (changes.isEmpty()) return
        broadcastToClients(clientHandler.clients) { client ->
            changes.forEach { op ->
                val rec = RecommendationsUtil.fromStatusBarNotification(context, op.notification)
                when (op) {
                    is RecOperation.Add -> client.onAddRecommendation(rec)
                    is RecOperation.Change -> client.onUpdateRecommendation(rec)
                    is RecOperation.Remove -> client.onRemoveRecommendation(rec)
                }
            }
        }
    }

    private fun tidyRecommendation(sbn: StatusBarNotification) {
        sbn.notification.apply {
            contentView = null
            bigContentView = null
        }
        processRecommendationImage(sbn)
    }

    private fun processRecommendationImage(sbn: StatusBarNotification) {
        if (!RecommendationsUtil.isRecommendation(sbn)) return

        val notif = sbn.notification
        val isPartner = RecommendationsUtil.isInPartnerRow(context, sbn)
        val img = notif.largeIcon ?: getRecomendationImage(sbn.key) ?: return

        if (isPartner) {
            notif.largeIcon = getResizedBitmap(img, true)
        } else {
            getResizedCardDimensions(img.width, img.height)?.let { dim ->
                notif.extras.putInt("notif_img_width", dim.x)
                notif.extras.putInt("notif_img_height", dim.y)
            }
            notif.largeIcon = getResizedBitmap(img, false)
        }
    }

    fun getRecomendationImage(key: String): Bitmap? {
        val sbn = notificationResolver?.getNotification(key) ?: return null
        return getResizedBitmap(sbn.notification.largeIcon, RecommendationsUtil.isInPartnerRow(context, sbn))
    }

    private fun getResizedBitmap(image: Bitmap?, isBanner: Boolean, lowRes: Boolean = false): Bitmap? {
        val (maxW, maxH) = if (isBanner) {
            bannerMaxWidth to bannerMaxHeight
        } else {
            val factor = if (lowRes) 0.1f else 1f
            (cardMaxWidth * factor).toInt() to (cardMaxHeight * factor).toInt()
        }
        return RecommendationsUtil.getSizeCappedBitmap(image, maxW, maxH)
    }

    private fun getResizedCardDimensions(imgWidth: Int, imgHeight: Int): Point? {
        if (imgWidth <= cardMaxWidth && imgHeight <= cardMaxHeight) {
            return Point(imgWidth, imgHeight)
        }
        if (imgWidth <= 0 || imgHeight <= 0) return Point(imgWidth, imgHeight)

        val scale = minOf(1f, cardMaxHeight.toFloat() / imgHeight)
        return if (scale < 1f || imgWidth > cardMaxWidth) {
            Point(
                minOf((imgWidth * scale).toInt(), cardMaxWidth),
                (imgHeight * scale).toInt()
            )
        } else Point(imgWidth, imgHeight)
    }

    fun cancelRecommendation(key: String) { notificationResolver?.cancelRecommendation(key) }

    fun getRecommendationsPackages(): List<String> {
        val installed = context.packageManager.getInstalledApplications(0)
            .filter { it.enabled && it.packageName != "android" }
            .map { it.packageName }
            .toSet()
        return dbHelper.loadRecommendationsPackages().filter { it in installed }
    }

    fun getBlacklistedPackages() = dbHelper.loadBlacklistedPackages()
    
    fun setBlacklistedPackages(packages: Array<String>) {
        dbHelper.saveBlacklistedPackages(packages)
        ranker.reload()
    }

    // Public API for notification service
    fun sendConnectionStatus(connected: Boolean) = clientHandler.enqueueConnectionStatus(connected)
    fun addNotification(sbn: StatusBarNotification) = clientHandler.enqueueNotificationPosted(sbn)
    fun removeNotification(sbn: StatusBarNotification) = clientHandler.enqueueNotificationRemoved(sbn)
    fun resetNotifications() = clientHandler.enqueueNotificationReset()
    fun addCaptivePortalNotification(sbn: StatusBarNotification) = clientHandler.enqueueCaptivePortalPosted(sbn)
    fun removeAllCaptivePortalNotifications() = clientHandler.enqueueAllCaptivePortalRemoved()

    companion object {
        @Volatile private var instance: RecommendationsManager? = null

        fun getInstance(context: Context, unbundled: Boolean, factory: RankerParametersFactory): RecommendationsManager {
            return instance ?: synchronized(this) {
                instance ?: RecommendationsManager(
                    context.applicationContext,
                    unbundled,
                    factory.create(context)
                ).also { instance = it }
            }
        }
    }
}

object RecommendationFlags{
    const val MSG_START = 0
    const val MSG_NOTIFICATION = 1
    const val MSG_NOTIFICATION_RESET = 2
    const val MSG_CONNECTION_STATUS = 3
    const val MSG_CAPTIVE_PORTAL_POSTED = 4
    const val MSG_CAPTIVE_PORTAL_REMOVED = 5
}