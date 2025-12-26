package com.amazon.tv.tvrecommendations

import android.app.PendingIntent
import android.graphics.Bitmap
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils

data class TvRecommendation(
    val packageName: String?,
    val key: String?,
    val postTime: Long = 0,
    val group: String? = null,
    val sortKey: String? = null,
    val contentIntent: PendingIntent? = null,
    val isAutoDismiss: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val color: Int = 0,
    val contentImage: Bitmap? = null,
    val backgroundImageUri: String? = null,
    val title: CharSequence? = null,
    val text: CharSequence? = null,
    val sourceName: CharSequence? = null,
    val badgeIcon: Int = 0,
    val progressMax: Int = 0,
    val progress: Int = 0,
    val score: Double = 0.0,
    val replacedPackageName: String? = null
) : Parcelable {

    val hasProgress: Boolean get() = progressMax > 0

    constructor(parcel: Parcel) : this(
        packageName = parcel.readString(),
        key = parcel.readString(),
        postTime = parcel.readLong(),
        group = parcel.readString(),
        sortKey = parcel.readString(),
        contentIntent = parcel.readParcelable(TvRecommendation::class.java.classLoader),
        isAutoDismiss = parcel.readInt() != 0,
        width = parcel.readInt(),
        height = parcel.readInt(),
        color = parcel.readInt(),
        contentImage = parcel.readParcelable(TvRecommendation::class.java.classLoader),
        backgroundImageUri = parcel.readString(),
        title = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel),
        text = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel),
        sourceName = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel),
        badgeIcon = parcel.readInt(),
        progressMax = parcel.readInt(),
        progress = parcel.readInt(),
        score = parcel.readDouble(),
        replacedPackageName = parcel.readString()
    )

    override fun writeToParcel(out: Parcel, flags: Int) {
        out.apply {
            writeString(packageName)
            writeString(key)
            writeLong(postTime)
            writeString(group)
            writeString(sortKey)
            writeParcelable(contentIntent, flags)
            writeInt(if (isAutoDismiss) 1 else 0)
            writeInt(width)
            writeInt(height)
            writeInt(color)
            writeParcelable(contentImage, flags)
            writeString(backgroundImageUri)
            TextUtils.writeToParcel(title, this, 0)
            TextUtils.writeToParcel(text, this, 0)
            TextUtils.writeToParcel(sourceName, this, 0)
            writeInt(badgeIcon)
            writeInt(progressMax)
            writeInt(progress)
            writeDouble(score)
            writeString(replacedPackageName)
        }
    }

    override fun describeContents() = 0

    override fun toString() = buildString {
        append("TvRecommendation(pkg=$packageName, title=$title, ")
        append("img=$contentImage, ${width}x$height, intent=$contentIntent, score=$score)")
    }

    companion object CREATOR : Parcelable.Creator<TvRecommendation> {
        override fun createFromParcel(parcel: Parcel) = TvRecommendation(parcel)
        override fun newArray(size: Int) = arrayOfNulls<TvRecommendation>(size)
    }
}