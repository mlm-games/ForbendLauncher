package com.amazon.tv.tvrecommendations.service

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.TextUtils
import com.amazon.tv.leanbacklauncher.R
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException

class DbHelper private constructor(
    private val context: Context,
    databaseName: String = "recommendations.db",
    private val migrationEnabled: Boolean = true
) : SQLiteOpenHelper(context, databaseName, null, 2) {

    @Volatile var mostRecentTimeStamp: Long = 0L
        private set

    @get:Throws(IOException::class)
    val recommendationMigrationFile: File
        get() = File(context.filesDir, "migration_recs")
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    interface Listener {
        fun onEntitiesLoaded(entities: HashMap<String, Entity>, blacklist: List<String>)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createAllTables(db)
        DateUtil.setInitialRankingAppliedFlag(context, tryMigrateState(db))
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1) {
            setHasRecommendationsTrue(db, context.resources.getStringArray(R.array.out_of_box_order))
            setHasRecommendationsTrue(db, ServicePartner.get(context).outOfBoxOrder)
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        removeAllTables(db)
        onCreate(db)
    }

    fun saveEntity(entity: Entity) {
        if (entity.key.isBlank()) return
        scope.launch { saveEntitySync(entity) }
    }

    fun removeEntity(key: String, fullRemoval: Boolean) {
        if (key.isBlank()) return
        scope.launch { removeEntitySync(key, fullRemoval) }
    }

    fun removeGroupData(key: String, group: String) {
        if (key.isBlank()) return
        scope.launch {
            val db = writableDatabase
            val args = arrayOf(key, group)
            db.delete("buckets", "key=? AND group_id=?", args)
            db.delete("buffer_scores", "key=? AND group_id=?", args)
        }
    }

    fun getEntities(listener: Listener) {
        scope.launch {
            val entities = loadEntitiesSync()
            val blacklist = loadBlacklistedPackages()
            withContext(Dispatchers.Main) {
                listener.onEntitiesLoaded(entities, blacklist)
            }
        }
    }

    fun loadRecommendationsPackages(): List<String> = 
        readableDatabase.query("entity", arrayOf("key"), 
            "key IS NOT NULL AND has_recs=1", null, null, null, "key")
            .use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList()
            }

    fun loadBlacklistedPackages(): List<String> = 
        readableDatabase.query("rec_blacklist", arrayOf("key"), 
            "key IS NOT NULL", null, null, null, null)
            .use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList()
            }

    fun saveBlacklistedPackages(packages: Array<String>) {
        writableDatabase.apply {
            beginTransaction()
            try {
                execSQL("DELETE FROM rec_blacklist")
                packages.forEach { pkg ->
                    insert("rec_blacklist", null, ContentValues().apply { put("key", pkg) })
                }
                setTransactionSuccessful()
            } finally { endTransaction() }
        }
    }

    private fun createAllTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS entity('key' TEXT PRIMARY KEY, notif_bonus REAL, bonus_timestamp INTEGER, oob_order INTEGER, has_recs INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS entity_scores('key' TEXT NOT NULL, component TEXT, entity_score INTEGER NOT NULL, last_opened INTEGER, PRIMARY KEY('key', component), FOREIGN KEY('key') REFERENCES entity('key'))")
        db.execSQL("CREATE TABLE IF NOT EXISTS rec_blacklist('key' TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE IF NOT EXISTS buckets('key' TEXT NOT NULL, group_id TEXT NOT NULL, last_updated INTEGER NOT NULL, PRIMARY KEY('key', group_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS buffer_scores(_id INTEGER NOT NULL, 'key' TEXT NOT NULL, group_id TEXT NOT NULL, day INTEGER NOT NULL, mClicks INTEGER, mImpressions INTEGER, PRIMARY KEY(_id, group_id, 'key'))")
    }

    private fun removeAllTables(db: SQLiteDatabase) {
        listOf("entity", "entity_scores", "rec_blacklist", "buckets", "buffer_scores")
            .forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
    }

    private fun saveEntitySync(entity: Entity) {
        val db = writableDatabase
        val key = entity.key
        
        val entityValues = ContentValues().apply {
            put("key", key)
            put("notif_bonus", entity.getBonus())
            put("bonus_timestamp", entity.getBonusTimeStamp())
            put("has_recs", if (entity.hasPostedRecommendations()) "1" else "0")
        }
        
        if (db.update("entity", entityValues, "key=?", arrayOf(key)) == 0) {
            db.insert("entity", null, entityValues)
        }

        entity.entityComponents.forEach { component ->
            val cv = ContentValues().apply {
                put("key", key)
                put("component", component)
                put("entity_score", entity.getOrder(component))
                put("last_opened", entity.getLastOpenedTimeStamp(component))
            }
            val whereClause = if (component == null) "key=? AND component IS NULL" else "key=? AND component=?"
            val whereArgs = if (component == null) arrayOf(key) else arrayOf(key, component)
            
            if (db.update("entity_scores", cv, whereClause, whereArgs) == 0) {
                db.insert("entity_scores", null, cv)
            }
        }

        entity.groupIds.forEach { groupId ->
            val groupCv = ContentValues().apply {
                put("key", key)
                put("group_id", groupId)
                put("last_updated", entity.getGroupTimeStamp(groupId))
            }
            
            if (db.update("buckets", groupCv, "key=? AND group_id=?", arrayOf(key, groupId)) == 0) {
                db.insert("buckets", null, groupCv)
            }

            entity.getSignalsBuffer(groupId)?.let { buffer ->
                repeat(buffer.size()) { i ->
                    val signals = buffer.getAt(i) ?: return@repeat
                    val day = buffer.getDayAt(i).takeIf { it != -1 } ?: return@repeat
                    
                    val signalCv = ContentValues().apply {
                        put("_id", i)
                        put("key", key)
                        put("group_id", groupId)
                        put("day", day)
                        put("mClicks", signals.mClicks)
                        put("mImpressions", signals.mImpressions)
                    }
                    
                    if (db.update("buffer_scores", signalCv, "key=? AND group_id=? AND _id=?", 
                            arrayOf(key, groupId, i.toString())) == 0) {
                        db.insert("buffer_scores", null, signalCv)
                    }
                }
            }
        }
    }

    private fun removeEntitySync(key: String, fullRemoval: Boolean) {
        val db = writableDatabase
        val args = arrayOf(key)
        
        if (fullRemoval) {
            db.delete("entity", "key=?", args)
        } else {
            db.update("entity", ContentValues().apply {
                put("key", key)
                put("notif_bonus", 0)
                put("bonus_timestamp", 0)
            }, "key=?", args)
        }
        
        db.delete("entity_scores", "key=?", args)
        db.delete("buckets", "key=?", args)
        db.delete("buffer_scores", "key=?", args)
        db.delete("rec_blacklist", "key=?", args)
    }

    private fun loadEntitiesSync(): HashMap<String, Entity> {
        val entities = HashMap<String, Entity>()
        val db = writableDatabase

        // Load entities
        db.query("entity", null, null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                val key = c.getString(c.getColumnIndexOrThrow("key"))
                if (key.isNullOrEmpty()) continue
                
                val bonus = c.getDoubleOrNull("notif_bonus") ?: 0.0
                val bonusTime = c.getLongOrNull("bonus_timestamp") ?: 0L
                val initialOrder = c.getLongOrNull("oob_order") ?: 0L
                val postedRec = c.getLongOrNull("has_recs") == 1L
                
                entities[key] = Entity(context, this, key, initialOrder, postedRec).apply {
                    if (bonusTime != 0L && bonus > 0.0) setBonusValues(bonus, bonusTime)
                }
            }
        }

        // Load entity scores
        db.query("entity_scores", null, null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                val key = c.getString(c.getColumnIndexOrThrow("key")) ?: continue
                val component = c.getStringOrNull("component")
                val entityScore = c.getLongOrNull("entity_score") ?: 0L
                val lastOpened = c.getLongOrNull("last_opened") ?: 0L
                
                synchronized(this) { if (mostRecentTimeStamp < lastOpened) mostRecentTimeStamp = lastOpened }
                
                entities[key]?.apply {
                    setOrder(component, entityScore)
                    setLastOpenedTimeStamp(component, lastOpened)
                }
            }
        }

        // Load buckets
        db.query("buckets", arrayOf("key", "group_id", "last_updated"), null, null, null, null, "key, last_updated").use { c ->
            while (c.moveToNext()) {
                val key = c.getString(0) ?: continue
                entities[key]?.addBucket(c.getString(1), c.getLong(2))
            }
        }

        // Load buffer scores
        db.query("buffer_scores", arrayOf("_id", "key", "group_id", "day", "mClicks", "mImpressions"), 
            null, null, null, null, "key, group_id, _id").use { c ->
            while (c.moveToNext()) {
                val key = c.getString(1) ?: continue
                val group = c.getString(2)
                val day = c.getInt(3).takeIf { it != -1 } ?: continue
                
                entities[key]?.getSignalsBuffer(group)?.set(
                    DateUtil.getDate(day)!!,
                    Signals(c.getInt(4), c.getInt(5))
                )
            }
        }

        return entities
    }

    private fun tryMigrateState(db: SQLiteDatabase): Boolean {
        // Simplified - return false if migration not needed
        return false
    }

    private fun setHasRecommendationsTrue(db: SQLiteDatabase, packages: Array<String>?) {
        packages?.forEach { pkg ->
            db.update("entity", ContentValues().apply { put("has_recs", 1) }, "key=?", arrayOf(pkg))
        }
    }

    companion object {
        @Volatile private var instance: DbHelper? = null
        
        fun getInstance(context: Context): DbHelper = instance ?: synchronized(this) {
            instance ?: DbHelper(context.applicationContext).also { instance = it }
        }
    }
}

// Cursor extensions
private fun android.database.Cursor.getStringOrNull(column: String) = 
    getColumnIndex(column).takeIf { it >= 0 }?.let { getString(it) }
private fun android.database.Cursor.getDoubleOrNull(column: String) = 
    getColumnIndex(column).takeIf { it >= 0 }?.let { getDouble(it) }
private fun android.database.Cursor.getLongOrNull(column: String) = 
    getColumnIndex(column).takeIf { it >= 0 }?.let { getLong(it) }