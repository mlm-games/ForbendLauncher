package com.amazon.tv.leanbacklauncher.util

import android.net.Uri

object SearchWidgetInfoContract {
    val ICON_CONTENT_URI: Uri = Uri.parse("content://com.google.android.katniss.search.WidgetInfoProvider/state")
    val SUGGESTIONS_CONTENT_URI: Uri = Uri.parse("content://com.google.android.katniss.search.WidgetInfoProvider/suggestions")
}