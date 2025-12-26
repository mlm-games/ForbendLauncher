package com.amazon.tv.tvrecommendations

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder

abstract class RecommendationsClient(private val context: Context) {
    
    private var boundService: IRecommendationsService? = null
    private var connectedOrConnecting = false
    private var connection: ServiceConnection? = null

    protected abstract fun onConnected(service: IRecommendationsService)
    protected abstract fun onDisconnected()

    fun connect() {
        connectedOrConnecting = true
        val serviceIntent = getServiceIntent() ?: return
        
        connection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                boundService = IRecommendationsService.Stub.asInterface(service)
                if (connectedOrConnecting) {
                    boundService?.let { onConnected(it) }
                }
            }

            override fun onServiceDisconnected(className: ComponentName) {
                boundService = null
                if (connectedOrConnecting) onDisconnected()
            }
        }
        context.bindService(serviceIntent, connection!!, Context.BIND_AUTO_CREATE)
    }

    fun disconnect() {
        connectedOrConnecting = false
        connection?.let {
            context.unbindService(it)
            connection = null
        }
    }

    protected open fun getServiceIntent(): Intent? {
        val component = getServiceComponent() ?: return null
        return Intent("RecommendationsService").setComponent(component)
    }

    fun getServiceComponent(): ComponentName? {
        val resolveInfoList = context.packageManager
            .queryIntentServices(Intent("RecommendationsService"), 0)
        
        if (resolveInfoList.isNullOrEmpty() || resolveInfoList.size != 1) return null
        
        return resolveInfoList[0].serviceInfo.let {
            ComponentName(it.packageName, it.name)
        }
    }

    companion object {
        fun clearReasonToString(clearReason: Int) = when (clearReason) {
            2 -> "CLEAR_RECOMMENDATIONS_DISABLED ($clearReason)"
            3 -> "CLEAR_RECOMMENDATIONS_PENDING ($clearReason)"
            4 -> "CLEAR_RECOMMENDATIONS_PENDING_DISABLED ($clearReason)"
            else -> "UNKNOWN ($clearReason)"
        }
    }
}