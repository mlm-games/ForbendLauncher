package com.amazon.tv.leanbacklauncher.notifications

import android.app.PendingIntent
import android.graphics.Bitmap
import android.os.Parcel
import android.os.Parcelable

data class NowPlayingCardData(
    var title: String? = null,
    var artist: String? = null,
    var albumArtist: String? = null,
    var albumTitle: String? = null,
    var year: Long = -1,
    var trackNumber: Long = -1,
    var duration: Long = -1,
    var playerPackage: String? = null,
    var artwork: Bitmap? = null,
    var badgeIcon: Bitmap? = null,
    var clickIntent: PendingIntent? = null,
    var launchColor: Int = 0
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        title = parcel.readString(),
        artist = parcel.readString(),
        albumArtist = parcel.readString(),
        albumTitle = parcel.readString(),
        year = parcel.readLong(),
        trackNumber = parcel.readLong(),
        duration = parcel.readLong(),
        playerPackage = parcel.readString(),
        artwork = parcel.readParcelable(Bitmap::class.java.classLoader),
        badgeIcon = parcel.readParcelable(Bitmap::class.java.classLoader)
    )

    override fun writeToParcel(out: Parcel, flags: Int) {
        out.apply {
            writeString(title)
            writeString(artist)
            writeString(albumArtist)
            writeString(albumTitle)
            writeLong(year)
            writeLong(trackNumber)
            writeLong(duration)
            writeString(playerPackage)
            writeParcelable(artwork, 0)
            writeParcelable(badgeIcon, 0)
        }
    }

    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<NowPlayingCardData> {
        override fun createFromParcel(parcel: Parcel) = NowPlayingCardData(parcel)
        override fun newArray(size: Int) = arrayOfNulls<NowPlayingCardData>(size)
    }
}