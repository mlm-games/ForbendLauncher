package com.amazon.tv.leanbacklauncher.migration

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import com.amazon.tv.leanbacklauncher.apps.AppsDbHelper
import java.io.FileNotFoundException
import androidx.core.net.toUri

class DbMigrationContentProvider(
    private var dbHelper: AppsDbHelper? = null
) : ContentProvider() {

    override fun onCreate() = true

    override fun openTypedAssetFile(uri: Uri, mimeTypeFilter: String, opts: Bundle?): AssetFileDescriptor {
        if (DbMigrationContract.CONTENT_URI == uri) {
            try {
                val file = getAppDbHelper()?.recommendationMigrationFile
                if (file != null) {
                    return AssetFileDescriptor(
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
                        0, -1
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot generate a recommendation migration file", e)
            }
            throw FileNotFoundException("Can't open $uri")
        }
        throw FileNotFoundException("Unsupported URI: $uri")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        if (DbMigrationContract.CONTENT_UPDATE_URI == uri) {
            getAppDbHelper()?.onMigrationComplete()
            return 1
        }
        return 0
    }

    private fun getAppDbHelper() = dbHelper ?: AppsDbHelper.getInstance(context!!)

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? =
        throw UnsupportedOperationException()
    override fun getType(uri: Uri): String? = throw UnsupportedOperationException()
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) =
        throw UnsupportedOperationException()

    companion object {
        private const val TAG = "DbMigrationCP"
    }

    interface DbMigrationContract {
        companion object {
            val CONTENT_UPDATE_URI: Uri? =
                "content://com.amazon.tv.tvrecommendations.migration/migrated".toUri()
            val CONTENT_URI: Uri? =
                "content://com.amazon.tv.tvrecommendations.migration/data".toUri()
        }
    }

}