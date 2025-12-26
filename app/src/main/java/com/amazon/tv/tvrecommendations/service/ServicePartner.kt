package com.amazon.tv.tvrecommendations.service

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.util.Log

class ServicePartner private constructor(
    private val packageName: String?,
    private val receiverName: String?,
    private val resources: Resources?
) {
    val outOfBoxOrder: Array<String>?
        get() {
            if (resources == null || packageName.isNullOrEmpty()) return null
            val resId = resources.getIdentifier("partner_out_of_box_order", "array", packageName)
            return if (resId != 0) resources.getStringArray(resId) else null
        }

    companion object {
        private val lock = Any()
        private var partner: ServicePartner? = null
        private var searched = false

        fun get(context: Context): ServicePartner {
            synchronized(lock) {
                if (!searched) {
                    val pm = context.packageManager
                    val info = getPartnerResolveInfo(pm)
                    
                    if (info != null) {
                        val pkgName = info.activityInfo.packageName
                        partner = runCatching {
                            ServicePartner(
                                pkgName,
                                info.activityInfo.name,
                                pm.getResourcesForApplication(pkgName)
                            )
                        }.getOrElse {
                            Log.w("ServicePartner", "Failed to find resources for $pkgName")
                            null
                        }
                    }
                    
                    searched = true
                    if (partner == null) {
                        partner = ServicePartner(null, null, null)
                    }
                }
                return partner!!
            }
        }

        private fun getPartnerResolveInfo(pm: android.content.pm.PackageManager): ResolveInfo? {
            val intent = Intent("com.google.android.leanbacklauncher.action.PARTNER_CUSTOMIZATION")
            return pm.queryBroadcastReceivers(intent, 0).firstOrNull()
        }
    }
}