package com.amazon.tv.leanbacklauncher.util

import android.content.AsyncTaskLoader
import android.content.Context
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

class TvSearchIconLoader(context: Context) : AsyncTaskLoader<Drawable?>(context) {
    private var contentObserver: ContentObserver? = null
    private var tvSearchIcon: Drawable? = null

    override fun onStartLoading() {
        tvSearchIcon?.let { deliverResult(it) }

        if (contentObserver == null) {
            contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = onContentChanged()
                override fun onChange(selfChange: Boolean, uri: Uri?) = onChange(selfChange)
            }
            try {
                context.contentResolver.registerContentObserver(
                    SearchWidgetInfoContract.ICON_CONTENT_URI, true, contentObserver!!
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Exception in onStartLoading() on registering content observer", e)
                contentObserver = null
            }
        }

        if (takeContentChanged() || tvSearchIcon == null) {
            forceLoad()
        }
    }

    override fun onReset() {
        onStopLoading()
        tvSearchIcon = null
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            contentObserver = null
        }
    }

    override fun loadInBackground(): Drawable? {
        tvSearchIcon = null
        // TODO: Implement actual loading if needed
        return tvSearchIcon
    }

    companion object {
        private const val TAG = "TvSearchIconLdr"
    }
}

class TvSearchSuggestionsLoader(context: Context) : AsyncTaskLoader<Array<String>?>(context) {
    private var contentObserver: ContentObserver? = null
    private var searchSuggestions: Array<String>? = null

    override fun onStartLoading() {
        searchSuggestions?.let { deliverResult(it) }

        if (contentObserver == null) {
            contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = onContentChanged()
                override fun onChange(selfChange: Boolean, uri: Uri?) = onChange(selfChange)
            }
            try {
                context.contentResolver.registerContentObserver(
                    SearchWidgetInfoContract.SUGGESTIONS_CONTENT_URI, true, contentObserver!!
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Exception in onStartLoading() on registering content observer", e)
                contentObserver = null
            }
        }

        if (takeContentChanged() || searchSuggestions == null) {
            forceLoad()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onReset() {
        onStopLoading()
        searchSuggestions = null
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            contentObserver = null
        }
    }

    override fun loadInBackground(): Array<String>? {
        searchSuggestions = null
        // TODO: Implement actual loading if needed
        return searchSuggestions
    }

    companion object {
        private const val TAG = "TvSearchSuggestionsLdr"
    }
}