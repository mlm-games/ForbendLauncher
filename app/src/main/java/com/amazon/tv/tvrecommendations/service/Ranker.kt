package com.amazon.tv.tvrecommendations.service

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import com.amazon.tv.leanbacklauncher.R
import java.util.*
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


object Actions {
    const val ACTION_ADD = 0
    const val ACTION_OPEN_LAUNCHPOINT = 1
    const val ACTION_OPEN_NOTIFICATION = 2
    const val ACTION_REMOVE = 3
    const val ACTION_NOTIFICATION_IMPRESSION = 4

    fun actionToString(action: Int) = when (action) {
        ACTION_ADD -> "ACTION_ADD"
        ACTION_OPEN_LAUNCHPOINT -> "ACTION_OPEN_LAUNCHPOINT"
        ACTION_OPEN_NOTIFICATION -> "ACTION_OPEN_NOTIFICATION"
        ACTION_REMOVE -> "ACTION_REMOVE"
        ACTION_NOTIFICATION_IMPRESSION -> "ACTION_NOTIFICATION_IMPRESSION"
        else -> "UNKNOWN ($action)"
    }
}

class Ranker(
    private val context: Context,
    private val dbHelper: DbHelper,
    rankerParameters: RankerParameters
) : DbHelper.Listener {

    companion object {
        private const val TAG = "Ranker"
        private lateinit var sRankerParameters: RankerParameters

        fun getGroupStarterScore() = sRankerParameters.getGroupStarterScore().toDouble()
        fun getInstallBonus() = sRankerParameters.getInstallBonus().toDouble()
        fun getBonusFadePeriod() = sRankerParameters.getBonusFadePeriodDays() * 8.64E7
    }

    private val appUsageStatistics = AppUsageStatistics(context)
    private var blacklistedPackages = mutableListOf<String>()
    private val cachedActions = LinkedList<CachedAction>()
    private var ctrNormalizer = Normalizer()
    private var entities = HashMap<String, Entity>()
    private val entitiesLock = Any()
    private val listeners = mutableListOf<RankingListener>()
    private var queryingScores = true

    private data class CachedAction(
        val key: String,
        val component: String?,
        val group: String?,
        val action: Int
    )

    fun interface RankingListener {
        fun onRankerReady()
    }

    init {
        sRankerParameters = rankerParameters
        dbHelper.getEntities(this)
    }

    fun addListener(listener: RankingListener) { listeners += listener }
    fun reload() { dbHelper.getEntities(this) }
    fun isBlacklisted(packageName: String) = packageName in blacklistedPackages
    fun hasBlacklistedPackages() = blacklistedPackages.isNotEmpty()

    fun onActionOpenLaunchPoint(key: String, group: String?) = onAction(key, null, group, Actions.ACTION_OPEN_LAUNCHPOINT)
    fun onActionOpenRecommendation(key: String, group: String?) = onAction(key, null, group, Actions.ACTION_OPEN_NOTIFICATION)
    fun onActionRecommendationImpression(key: String, group: String?) = onAction(key, null, group, Actions.ACTION_NOTIFICATION_IMPRESSION)
    fun onActionPackageAdded(packageName: String) = onAction(packageName, null, null, Actions.ACTION_ADD)
    fun onActionPackageRemoved(packageName: String) = onAction(packageName, null, null, Actions.ACTION_REMOVE)

    private fun onAction(key: String, component: String?, group: String?, actionType: Int) {
        if (key.isEmpty()) return

        synchronized(cachedActions) {
            if (queryingScores) {
                cachedActions.add(CachedAction(key, component, group, actionType))
                return
            }
        }

        synchronized(entitiesLock) {
            val entity = entities[key]

            if (actionType == Actions.ACTION_REMOVE) {
                if (entity != null) {
                    if (entity.getOrder(component) != 0L) {
                        entity.onAction(actionType, component, null)
                        dbHelper.removeEntity(key, false)
                    }
                } else {
                    entities.remove(key)
                    dbHelper.removeEntity(key, true)
                }
            } else {
                val e = entity ?: Entity(context, dbHelper, key).also { entities[key] = it }
                e.onAction(actionType, component, group)
                dbHelper.saveEntity(e)
            }
        }
    }

    fun markPostedRecommendations(packageName: String) {
        synchronized(entitiesLock) {
            val entity = entities.getOrPut(packageName) {
                Entity(context, dbHelper, packageName)
            }
            if (!entity.hasPostedRecommendations()) {
                entity.markPostedRecommendations()
                dbHelper.saveEntity(entity)
            }
        }
    }

    fun prepNormalizationValues() {
        ctrNormalizer = Normalizer()
        entities.values.forEach { it.addNormalizeableValues(ctrNormalizer) }
    }

    fun calculateAdjustedScore(sbn: StatusBarNotification, position: Int, forceFirst: Boolean) {
        val baseScore = getBaseNotificationScore(sbn)
        val adjustedScore = baseScore * (position + 1).toDouble().pow(-sRankerParameters.getSpreadFactor().toDouble()) +
                if (forceFirst) 1.0 else 0.0
        cacheScore(sbn, adjustedScore)
    }

    private fun getRawScore(notification: Notification): Double {
        return try {
            max(0.0, min(1.0, notification.sortKey?.toDouble() ?: 0.5))
        } catch (e: Exception) {
            (notification.priority + 2).toDouble() / 4.0
        }
    }

    fun getBaseNotificationScore(sbn: StatusBarNotification): Double {
        val extras = sbn.notification.extras
        if (extras?.containsKey("cached_base_score") == true) {
            return extras.getDouble("cached_base_score")
        }

        var value = -100.0
        val packageName = sbn.packageName
        val notif = sbn.notification

        if (notif != null && !packageName.isNullOrEmpty()) {
            val entity = synchronized(entitiesLock) { entities[packageName] }

            if (entity != null) {
                var ctr = getCachedCtr(sbn)
                if (ctr == -1.0) {
                    ctr = entity.getCtr(ctrNormalizer, notif.group)
                    cacheCtr(sbn, ctr)
                }

                val rawScore = getRawScore(notif)
                val appUsageScore = appUsageStatistics.getAppUsageScore(entity.key)
                val amortizedBonus = entity.getAmortizedBonus()

                val scorePerturbation = extras?.getCharSequence("android.title")?.let {
                    (it.hashCode().toDouble() / 2.147483647E9 / 2.0) + 0.5
                } ?: 0.0

                val combined = (0.25 * ctr) + (0.25 * amortizedBonus) + (0.25 * rawScore) +
                        (0.25 * appUsageScore) + (0.01 * scorePerturbation)
                value = (1.0 / (1.0 + exp(-combined)) - 0.5) * 2.0
            }
        }

        sbn.notification.extras = (extras ?: Bundle()).apply {
            putDouble("cached_base_score", value)
        }
        return value
    }

    private fun getCachedCtr(sbn: StatusBarNotification): Double =
        sbn.notification.extras?.getDouble("cached_ctr", -1.0) ?: -1.0

    private fun cacheCtr(sbn: StatusBarNotification, ctr: Double) {
        sbn.notification.extras = (sbn.notification.extras ?: Bundle()).apply {
            putDouble("cached_ctr", ctr)
        }
    }

    fun getCachedNotificationScore(sbn: StatusBarNotification): Double =
        sbn.notification.extras?.getDouble("cached_score", -1.0) ?: -1.0

    private fun cacheScore(sbn: StatusBarNotification, score: Double) {
        sbn.notification.extras = (sbn.notification.extras ?: Bundle()).apply {
            putDouble("cached_score", score)
        }
    }

    override fun onEntitiesLoaded(entities: HashMap<String, Entity>, blacklistedPackages: List<String>) {
        synchronized(entitiesLock) {
            this.entities = entities
            this.blacklistedPackages = blacklistedPackages.toMutableList()
        }

        synchronized(cachedActions) {
            queryingScores = false
            while (cachedActions.isNotEmpty()) {
                val action = cachedActions.remove()
                onAction(action.key, action.component, action.group, action.action)
            }

            if (!DateUtil.initialRankingApplied(context)) {
                val outOfBoxOrder = context.resources.getStringArray(R.array.out_of_box_order)
                val partnerOutOfBoxOrder = ServicePartner.get(context).outOfBoxOrder
                val partnerLength = partnerOutOfBoxOrder?.size ?: 0
                val totalOrderings = outOfBoxOrder.size + partnerLength

                partnerOutOfBoxOrder?.let { applyOutOfBoxOrdering(it, 0, totalOrderings) }
                applyOutOfBoxOrdering(outOfBoxOrder, partnerLength, totalOrderings)
                DateUtil.setInitialRankingAppliedFlag(context, true)
            }
        }

        listeners.forEach { it.onRankerReady() }
    }

    private fun applyOutOfBoxOrdering(order: Array<String>?, offsetEntities: Int, totalEntities: Int) {
        if (order.isNullOrEmpty() || offsetEntities < 0 || totalEntities < order.size + offsetEntities) return

        val entitiesBelow = totalEntities - offsetEntities - order.size
        val bonusSum = 0.5 * totalEntities * (totalEntities + 1)

        order.reversed().forEachIndexed { i, key ->
            if (key !in entities) {
                val score = entitiesBelow + i + 1
                val e = Entity(context, dbHelper, key, score.toLong(), true).apply {
                    setBonusValues(
                        sRankerParameters.getOutOfBoxBonus() * (score.toDouble() / bonusSum),
                        Date().time
                    )
                }
                entities[key] = e
                dbHelper.saveEntity(e)
            }
        }
    }
}
