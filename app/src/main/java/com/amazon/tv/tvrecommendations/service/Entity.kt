package com.amazon.tv.tvrecommendations.service

import android.content.Context
import java.util.*

class Entity(
    private val context: Context,
    private val dbHelper: DbHelper?,
    val key: String,
    initialOrder: Long = 0,
    private var hasPostedRecommendations: Boolean = false
) {
    private val bucketList = linkedMapOf<String, Bucket>()
    private val signalsAggregator = SignalsAggregator()
    private val lastOpened = mutableMapOf<String?, Long>()
    private val rankOrder = mutableMapOf<String?, Long>(null to initialOrder)
    
    private var bonus = 0.0
    private var bonusTime = 0L

    val entityComponents: Set<String?> get() = rankOrder.keys
    val groupIds: List<String> get() = bucketList.keys.toList()

    fun getLastOpenedTimeStamp(component: String?): Long = 
        lastOpened[component] ?: lastOpened[null] ?: 0L

    fun setLastOpenedTimeStamp(component: String?, timestamp: Long) {
        lastOpened[component] = timestamp
    }

    fun getOrder(component: String?): Long {
        var order = rankOrder[component]
        if ((order == null || order == 0L) && rankOrder.size == 1) {
            order = rankOrder[null] ?: 0L
            setOrder(component, order)
        }
        return order ?: 0L
    }

    fun setOrder(component: String?, order: Long) {
        rankOrder[component] = order
    }

    fun hasPostedRecommendations() = hasPostedRecommendations
    fun markPostedRecommendations() { hasPostedRecommendations = true }

    fun getBonus() = bonus
    fun getBonusTimeStamp() = bonusTime

    fun setBonusValues(bonus: Double, timestamp: Long) {
        if (timestamp - System.currentTimeMillis() >= Ranker.getBonusFadePeriod()) {
            this.bonus = 0.0
            this.bonusTime = 0
        } else {
            this.bonus = bonus
            this.bonusTime = timestamp
        }
    }

    fun getAmortizedBonus(): Double {
        if (bonusTime == 0L && bonus == 0.0) return 0.0
        val factor = 1.0 - (System.currentTimeMillis() - bonusTime) / Ranker.getBonusFadePeriod()
        return if (factor >= 0) bonus * factor else 0.0
    }

    @Synchronized
    fun onAction(actionType: Int, component: String?, group: String?) {
        val date = Date()
        var time = date.time
        if (dbHelper != null && dbHelper.mostRecentTimeStamp >= time) {
            time = dbHelper.mostRecentTimeStamp + 1
        }

        when (actionType) {
            ACTION_INSTALL -> {
                if (getLastOpenedTimeStamp(component) == 0L) {
                    addBonusValue(Ranker.getInstallBonus())
                    setLastOpenedTimeStamp(component, time)
                }
            }
            ACTION_OPEN -> setLastOpenedTimeStamp(component, time)
            ACTION_UNINSTALL -> {
                lastOpened.clear()
                bonus = 0.0
                bonusTime = 0
                bucketList.clear()
            }
            ACTION_CLICK, ACTION_IMPRESSION -> {
                val bucket = getOrAddBucket(group.orEmpty())
                val buffer = bucket.buffer
                val signals = buffer[date] ?: Signals()
                
                when (actionType) {
                    ACTION_CLICK -> signals.mClicks++
                    ACTION_IMPRESSION -> signals.mImpressions++
                }
                buffer[date] = signals
                touchBucket(group.orEmpty())
            }
        }
    }

    @Synchronized
    fun addBucket(group: String?, timestamp: Long): Bucket {
        val safeGroup = group.orEmpty()
        
        bucketList[safeGroup]?.let { existing ->
            existing.timestamp = timestamp
            bucketList.remove(safeGroup)
            bucketList[safeGroup] = existing
            return existing
        }

        if (bucketList.size >= MAX_BUCKETS) {
            val removedGroup = bucketList.keys.first()
            bucketList.remove(removedGroup)
            dbHelper?.removeGroupData(key, removedGroup)
        }

        return Bucket(timestamp).also { bucketList[safeGroup] = it }
    }

    fun getGroupTimeStamp(group: String?) = bucketList[group.orEmpty()]?.timestamp ?: 0L
    fun getSignalsBuffer(group: String?) = bucketList[group.orEmpty()]?.buffer

    @Synchronized
    fun getCtr(ctrNormalizer: Normalizer, group: String?): Double {
        val bucket = bucketList[group.orEmpty()] ?: return 0.0
        val aggregatedCtr = bucket.buffer.getAggregatedScore(signalsAggregator)
        return if (aggregatedCtr != -1.0) ctrNormalizer.getNormalizedValue(aggregatedCtr) else 0.0
    }

    @Synchronized
    fun addNormalizeableValues(ctrNormalizer: Normalizer) {
        bucketList.values.forEach { bucket ->
            if (bucket.buffer.hasData()) {
                ctrNormalizer.addNormalizeableValue(bucket.buffer.getAggregatedScore(signalsAggregator))
            }
        }
    }

    private fun addBonusValue(newBonus: Double) {
        bonus = getAmortizedBonus() + newBonus
        bonusTime = System.currentTimeMillis()
    }

    private fun getOrAddBucket(group: String) = 
        bucketList[group] ?: addBucket(group, System.currentTimeMillis())

    private fun touchBucket(group: String) {
        bucketList.remove(group)?.let {
            it.updateTimestamp()
            bucketList[group] = it
        }
    }

    companion object {
        const val ACTION_INSTALL = 0
        const val ACTION_OPEN = 1
        const val ACTION_CLICK = 2
        const val ACTION_UNINSTALL = 3
        const val ACTION_IMPRESSION = 4
        private const val MAX_BUCKETS = 100
    }
}