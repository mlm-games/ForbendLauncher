package com.amazon.tv.tvrecommendations

import android.graphics.Bitmap
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.Parcelable

interface IRecommendationsClient : IInterface {

    @Throws(android.os.RemoteException::class)
    fun onServiceStatusChanged(isReady: Boolean)

    @Throws(android.os.RemoteException::class)
    fun onClearRecommendations(reason: Int)

    @Throws(android.os.RemoteException::class)
    fun onRecommendationBatchStart()

    @Throws(android.os.RemoteException::class)
    fun onAddRecommendation(recommendation: TvRecommendation?)

    @Throws(android.os.RemoteException::class)
    fun onUpdateRecommendation(recommendation: TvRecommendation?)

    @Throws(android.os.RemoteException::class)
    fun onRemoveRecommendation(recommendation: TvRecommendation?)

    @Throws(android.os.RemoteException::class)
    fun onRecommendationBatchEnd()

    abstract class Stub : Binder(), IRecommendationsClient {
        init { attachInterface(this, DESCRIPTOR) }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            data.enforceInterface(DESCRIPTOR)
            return when (code) {
                1 -> { onServiceStatusChanged(data.readInt() != 0); true }
                2 -> { onClearRecommendations(data.readInt()); true }
                3 -> { onRecommendationBatchStart(); true }
                4 -> { onAddRecommendation(data.readRecommendation()); true }
                5 -> { onUpdateRecommendation(data.readRecommendation()); true }
                6 -> { onRemoveRecommendation(data.readRecommendation()); true }
                7 -> { onRecommendationBatchEnd(); true }
                else -> super.onTransact(code, data, reply, flags)
            }
        }

        private class Proxy(private val remote: IBinder) : IRecommendationsClient {
            override fun asBinder(): IBinder = remote

            override fun onServiceStatusChanged(isReady: Boolean) =
                remote.transactOneWay(1) { writeInt(if (isReady) 1 else 0) }

            override fun onClearRecommendations(reason: Int) =
                remote.transactOneWay(2) { writeInt(reason) }

            override fun onRecommendationBatchStart() = remote.transactOneWay(3) {}
            override fun onRecommendationBatchEnd() = remote.transactOneWay(7) {}

            override fun onAddRecommendation(recommendation: TvRecommendation?) =
                remote.transactOneWay(4) { writeRecommendation(recommendation) }

            override fun onUpdateRecommendation(recommendation: TvRecommendation?) =
                remote.transactOneWay(5) { writeRecommendation(recommendation) }

            override fun onRemoveRecommendation(recommendation: TvRecommendation?) =
                remote.transactOneWay(6) { writeRecommendation(recommendation) }
        }

        companion object {
            private const val DESCRIPTOR = "IRecommendationsClient"

            @JvmStatic
            fun asInterface(obj: IBinder?): IRecommendationsClient? {
                obj ?: return null
                return obj.queryLocalInterface(DESCRIPTOR) as? IRecommendationsClient
                    ?: Proxy(obj)
            }
        }
    }
}

interface IRecommendationsService : IInterface {

    @Throws(android.os.RemoteException::class) fun getApiVersion(): Int
    @Throws(android.os.RemoteException::class) fun registerRecommendationsClient(client: IRecommendationsClient?, version: Int)
    @Throws(android.os.RemoteException::class) fun unregisterRecommendationsClient(client: IRecommendationsClient?)
    @Throws(android.os.RemoteException::class) fun registerPartnerRowClient(client: IRecommendationsClient?, version: Int)
    @Throws(android.os.RemoteException::class) fun unregisterPartnerRowClient(client: IRecommendationsClient?)
    @Throws(android.os.RemoteException::class) fun dismissRecommendation(key: String?)
    @Throws(android.os.RemoteException::class) fun getImageForRecommendation(key: String?): Bitmap?
    @Throws(android.os.RemoteException::class) fun onActionOpenLaunchPoint(component: String?, group: String?)
    @Throws(android.os.RemoteException::class) fun onActionOpenRecommendation(component: String?, group: String?)
    @Throws(android.os.RemoteException::class) fun onActionRecommendationImpression(component: String?, group: String?)
    @Throws(android.os.RemoteException::class) fun getRecommendationsPackages(): Array<String>?
    @Throws(android.os.RemoteException::class) fun getBlacklistedPackages(): Array<String>?
    @Throws(android.os.RemoteException::class) fun setBlacklistedPackages(blacklist: Array<String>?)

    abstract class Stub : Binder(), IRecommendationsService {
        init { attachInterface(this, DESCRIPTOR) }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            data.enforceInterface(DESCRIPTOR)
            reply?.let { r ->
                when (code) {
                    1 -> { r.writeNoException(); r.writeInt(getApiVersion()) }
                    2 -> { registerRecommendationsClient(IRecommendationsClient.Stub.asInterface(data.readStrongBinder()), data.readInt()); r.writeNoException() }
                    3 -> { unregisterRecommendationsClient(IRecommendationsClient.Stub.asInterface(data.readStrongBinder())); r.writeNoException() }
                    4 -> { registerPartnerRowClient(IRecommendationsClient.Stub.asInterface(data.readStrongBinder()), data.readInt()); r.writeNoException() }
                    5 -> { unregisterPartnerRowClient(IRecommendationsClient.Stub.asInterface(data.readStrongBinder())); r.writeNoException() }
                    6 -> { dismissRecommendation(data.readString()); r.writeNoException() }
                    7 -> { r.writeNoException(); r.writeParcelable(getImageForRecommendation(data.readString())) }
                    8 -> { onActionOpenLaunchPoint(data.readString(), data.readString()); r.writeNoException() }
                    9 -> { onActionOpenRecommendation(data.readString(), data.readString()); r.writeNoException() }
                    10 -> { onActionRecommendationImpression(data.readString(), data.readString()); r.writeNoException() }
                    11 -> { r.writeNoException(); r.writeStringArray(getRecommendationsPackages()) }
                    12 -> { r.writeNoException(); r.writeStringArray(getBlacklistedPackages()) }
                    13 -> { setBlacklistedPackages(data.createStringArray()); r.writeNoException() }
                    else -> return super.onTransact(code, data, reply, flags)
                }
            }
            return true
        }

        private class Proxy(private val remote: IBinder) : IRecommendationsService {
            override fun asBinder(): IBinder = remote

            override fun getApiVersion() = remote.transact(1) { readInt() }

            override fun registerRecommendationsClient(client: IRecommendationsClient?, version: Int) =
                remote.transactVoid(2) { writeStrongBinder(client?.asBinder()); writeInt(version) }

            override fun unregisterRecommendationsClient(client: IRecommendationsClient?) =
                remote.transactVoid(3) { writeStrongBinder(client?.asBinder()) }

            override fun registerPartnerRowClient(client: IRecommendationsClient?, version: Int) =
                remote.transactVoid(4) { writeStrongBinder(client?.asBinder()); writeInt(version) }

            override fun unregisterPartnerRowClient(client: IRecommendationsClient?) =
                remote.transactVoid(5) { writeStrongBinder(client?.asBinder()) }

            override fun dismissRecommendation(key: String?) =
                remote.transactVoid(6) { writeString(key) }

            override fun getImageForRecommendation(key: String?): Bitmap? =
                remote.transact(7, { writeString(key) }) { readParcelable(Bitmap::class.java.classLoader) }

            override fun onActionOpenLaunchPoint(component: String?, group: String?) =
                remote.transactVoid(8) { writeString(component); writeString(group) }

            override fun onActionOpenRecommendation(component: String?, group: String?) =
                remote.transactVoid(9) { writeString(component); writeString(group) }

            override fun onActionRecommendationImpression(component: String?, group: String?) =
                remote.transactVoid(10) { writeString(component); writeString(group) }

            override fun getRecommendationsPackages(): Array<String>? =
                remote.transact(11) { createStringArray() }

            override fun getBlacklistedPackages(): Array<String>? =
                remote.transact(12) { createStringArray() }

            override fun setBlacklistedPackages(blacklist: Array<String>?) =
                remote.transactVoid(13) { writeStringArray(blacklist) }
        }

        companion object {
            private const val DESCRIPTOR = "IRecommendationsService"

            @JvmStatic
            fun asInterface(obj: IBinder?): IRecommendationsService? {
                obj ?: return null
                return obj.queryLocalInterface(DESCRIPTOR) as? IRecommendationsService
                    ?: Proxy(obj)
            }
        }
    }
}

// Parcel Extensions
private fun Parcel.readRecommendation(): TvRecommendation? =
    if (readInt() != 0) TvRecommendation.CREATOR.createFromParcel(this) else null

private fun Parcel.writeRecommendation(rec: TvRecommendation?) {
    if (rec != null) { writeInt(1); rec.writeToParcel(this, 0) } else writeInt(0)
}

private fun Parcel.writeParcelable(p: Parcelable?) {
    if (p != null) { writeInt(1); p.writeToParcel(this, Parcelable.PARCELABLE_WRITE_RETURN_VALUE) }
    else writeInt(0)
}

private inline fun IBinder.transactOneWay(code: Int, block: Parcel.() -> Unit) {
    val data = Parcel.obtain()
    try {
        data.writeInterfaceToken("IRecommendationsClient")
        data.block()
        transact(code, data, null, IBinder.FLAG_ONEWAY)
    } finally { data.recycle() }
}

private inline fun <T> IBinder.transact(code: Int, write: Parcel.() -> Unit = {}, read: Parcel.() -> T): T {
    val data = Parcel.obtain()
    val reply = Parcel.obtain()
    try {
        data.writeInterfaceToken("IRecommendationsService")
        data.write()
        transact(code, data, reply, 0)
        reply.readException()
        return reply.read()
    } finally { reply.recycle(); data.recycle() }
}

private inline fun IBinder.transactVoid(code: Int, block: Parcel.() -> Unit) {
    transact(code, block) {}
}