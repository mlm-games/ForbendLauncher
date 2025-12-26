package com.amazon.tv.leanbacklauncher.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.DeadObjectException
import android.os.RemoteException
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.amazon.tv.leanbacklauncher.recommendations.SwitchingRecommendationsClient
import com.amazon.tv.leanbacklauncher.util.CSyncTask
import com.amazon.tv.tvrecommendations.IRecommendationsService
import kotlin.math.max

class RecommendationsPreferenceManager(context: Context) {
    private val mContext: Context = context.applicationContext

    interface LoadBlacklistCountCallback {
        fun onBlacklistCountLoaded(blacklistCount: Int)
    }

    interface LoadRecommendationPackagesCallback {
        fun onRecommendationPackagesLoaded(info: List<PackageInfo>?)
    }

    private abstract class AsyncRecommendationsClient(context: Context?) :
        SwitchingRecommendationsClient(context) {

        @Throws(RemoteException::class)
        protected abstract fun callServiceInBackground(iRecommendationsService: IRecommendationsService)
        override fun onConnected(service: IRecommendationsService) {
            Task(this).execute(service)
        }

        override fun onDisconnected() {}
        protected open fun onPostExecute() {}

        companion object {
            private class Task(private val asyncRecommendationsClient: AsyncRecommendationsClient) :
                CSyncTask<IRecommendationsService, Void?, Boolean>("RecsTask") {

                override fun doInBackground(vararg params: IRecommendationsService?): Boolean {
                    try {
                        params[0]?.let { asyncRecommendationsClient.callServiceInBackground(it) }
                    } catch (e: DeadObjectException) {
                        Log.e("RecPrefManager", "Rec service connection broken", e)
                        return true
                    } catch (e2: RemoteException) {
                        Log.e("RecPrefManager", "Call to recommendation service failed", e2)
                    } finally {
                        asyncRecommendationsClient.disconnect()
                    }
                    return false
                }

                override fun onPostExecute(retry: Boolean?) {
                    if (retry == true) {
                        Log.d("RecPrefManager", "Task failed, retrying")
                        asyncRecommendationsClient.connect()
                        return
                    }
                    asyncRecommendationsClient.onPostExecute()
                }
            }
        }
    }

    private class LoadBlacklistCountTask(
        context: Context,
        private val mCallback: LoadBlacklistCountCallback
    ) : AsyncRecommendationsClient(context) {
        private var mBlacklistedPackageCount = 0
        private val mPackageManager: PackageManager = context.packageManager
        override fun callServiceInBackground(iRecommendationsService: IRecommendationsService) {
            try {
                val blacklist = iRecommendationsService.getBlacklistedPackages()
                blacklist?.let {
                    mBlacklistedPackageCount = blacklist.size
                    for (pkg in blacklist) {
                        try {
                            mPackageManager.getPackageInfo(pkg, 0)
                        } catch (e: PackageManager.NameNotFoundException) {
                            mBlacklistedPackageCount--
                        }
                    }
                }
            } catch (e2: RemoteException) {
                mBlacklistedPackageCount = 0
            }
        }

        override fun onPostExecute() {
            mCallback.onBlacklistCountLoaded(max(0, mBlacklistedPackageCount))
        }

    }

    private class LoadRecommendationPackagesTask(
        private val mContext: Context,
        private val mCallback: LoadRecommendationPackagesCallback
    ) : AsyncRecommendationsClient(mContext) {
        private var mPackages: MutableList<PackageInfo>? = null

        @Throws(RemoteException::class)
        override fun callServiceInBackground(iRecommendationsService: IRecommendationsService) {
            val packages = iRecommendationsService.getRecommendationsPackages() ?: return
            val blacklistedPackages = iRecommendationsService.getBlacklistedPackages()
                ?.toSet() ?: emptySet()

            mPackages = ArrayList(packages.size)
            val pm = mContext.packageManager

            for (packageName in packages) {
                try {
                    val info = PackageInfo().apply {
                        this.packageName = packageName
                    }
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val res = pm.getResourcesForApplication(packageName)

                    info.appTitle = pm.getApplicationLabel(appInfo)
                    info.banner = resolveBanner(pm, res, appInfo, packageName)

                    if (info.banner == null && appInfo.icon != 0) {
                        info.icon = ResourcesCompat.getDrawable(res, appInfo.icon, null)
                    }
                    if (info.appTitle.isNullOrEmpty()) {
                        info.appTitle = packageName
                    }
                    if (info.banner == null && info.icon == null) {
                        info.icon = ContextCompat.getDrawable(mContext, 17301651)
                    }

                    info.blacklisted = packageName in blacklistedPackages
                    mPackages?.add(info)
                } catch (e: PackageManager.NameNotFoundException) {
                    // Package not installed, skip
                }
            }
        }

        private fun resolveBanner(
            pm: PackageManager,
            res: android.content.res.Resources,
            appInfo: android.content.pm.ApplicationInfo,
            packageName: String
        ): Drawable? {
            if (appInfo.banner != 0) {
                return ResourcesCompat.getDrawable(res, appInfo.banner, null)
            }

            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory("android.intent.category.LEANBACK_LAUNCHER")
                setPackage(packageName)
            }

            val resolveInfo = pm.resolveActivity(intent, 0)?.activityInfo ?: return null

            return when {
                resolveInfo.banner != 0 ->
                    ResourcesCompat.getDrawable(res, resolveInfo.banner, null)
                resolveInfo.logo != 0 ->
                    ResourcesCompat.getDrawable(res, resolveInfo.logo, null)
                else -> null
            }
        }

        override fun onPostExecute() {
            mCallback.onRecommendationPackagesLoaded(mPackages)
        }
    }

    class PackageInfo {
        var appTitle: CharSequence? = null
        var banner: Drawable? = null
        var blacklisted = false
        var icon: Drawable? = null
        var packageName: String? = null
    }

    private class SaveBlacklistTask(
        context: Context?,
        private val mPackageName: String?,
        private val mBlacklisted: Boolean
    ) : AsyncRecommendationsClient(context) {

        @Throws(RemoteException::class)
        override fun callServiceInBackground(iRecommendationsService: IRecommendationsService) {
            val currentBlacklist = iRecommendationsService.getBlacklistedPackages() ?: emptyArray()
            val blacklist = currentBlacklist.toMutableList()

            when {
                mBlacklisted && mPackageName !in blacklist -> blacklist.add(mPackageName!!)
                !mBlacklisted -> blacklist.remove(mPackageName)
            }

            iRecommendationsService.setBlacklistedPackages(blacklist.toTypedArray())
        }
    }

    fun loadBlacklistCount(callback: LoadBlacklistCountCallback) {
        LoadBlacklistCountTask(mContext, callback).connect()
    }

    fun loadRecommendationsPackages(callback: LoadRecommendationPackagesCallback) {
        LoadRecommendationPackagesTask(mContext, callback).connect()
    }

    fun savePackageBlacklisted(packageName: String?, blacklisted: Boolean) {
        SaveBlacklistTask(mContext, packageName, blacklisted).connect()
    }

}