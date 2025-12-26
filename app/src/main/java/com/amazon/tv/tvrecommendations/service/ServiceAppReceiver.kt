package com.amazon.tv.tvrecommendations.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class ServiceAppReceiver(private val listener: Listener) : BroadcastReceiver() {

    interface Listener {
        fun onPackageAdded(packageName: String)
        fun onPackageChanged(packageName: String)
        fun onPackageFullyRemoved(packageName: String)
        fun onPackageRemoved(packageName: String)
        fun onPackageReplaced(packageName: String)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart?.takeIf { it.isNotEmpty() } ?: return
        
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> listener.onPackageAdded(packageName)
            Intent.ACTION_PACKAGE_CHANGED -> listener.onPackageChanged(packageName)
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> listener.onPackageFullyRemoved(packageName)
            Intent.ACTION_PACKAGE_REMOVED -> {
                if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    listener.onPackageRemoved(packageName)
                }
            }
            Intent.ACTION_PACKAGE_REPLACED -> listener.onPackageReplaced(packageName)
        }
    }

    companion object {
        fun getIntentFilter() = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
    }
}

class ServiceAppListener(
    private val context: Context,
    private val ranker: Ranker
) : ServiceAppReceiver.Listener {

    private val serviceAppReceiver = ServiceAppReceiver(this)
    private var externalAppsReceiver: BroadcastReceiver? = null

    fun onCreate() {
        context.registerReceiver(serviceAppReceiver, ServiceAppReceiver.getIntentFilter())
        registerExternalAppsReceiver()
    }

    fun onDestroy() {
        context.unregisterReceiver(serviceAppReceiver)
        externalAppsReceiver?.let { context.unregisterReceiver(it) }
    }

    override fun onPackageAdded(packageName: String) = ranker.onActionPackageAdded(packageName)
    override fun onPackageChanged(packageName: String) {}
    override fun onPackageFullyRemoved(packageName: String) = ranker.onActionPackageRemoved(packageName)
    override fun onPackageRemoved(packageName: String) = ranker.onActionPackageRemoved(packageName)
    override fun onPackageReplaced(packageName: String) {}

    private fun registerExternalAppsReceiver() {
        externalAppsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Handle external packages status change if needed
            }
        }
        context.registerReceiver(externalAppsReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
        })
    }
}