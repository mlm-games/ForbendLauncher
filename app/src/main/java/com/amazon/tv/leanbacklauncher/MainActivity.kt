package com.amazon.tv.leanbacklauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_NO_CREATE
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.media.tv.TvContract
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.OnHierarchyChangeListener
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.accessibility.AccessibilityManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.view.isNotEmpty
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.amazon.tv.firetv.leanbacklauncher.apps.AppInfoActivity
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences
import com.amazon.tv.firetv.leanbacklauncher.apps.RowType
import com.amazon.tv.firetv.tvrecommendations.NotificationListenerMonitor
import com.amazon.tv.leanbacklauncher.SearchOrbView.SearchLaunchListener
import com.amazon.tv.leanbacklauncher.animation.AnimatorLifecycle
import com.amazon.tv.leanbacklauncher.animation.AnimatorLifecycle.OnAnimationFinishedListener
import com.amazon.tv.leanbacklauncher.animation.EditModeMassFadeAnimator
import com.amazon.tv.leanbacklauncher.animation.EditModeMassFadeAnimator.EditMode
import com.amazon.tv.leanbacklauncher.animation.ForwardingAnimatorSet
import com.amazon.tv.leanbacklauncher.animation.LauncherDismissAnimator
import com.amazon.tv.leanbacklauncher.animation.LauncherLaunchAnimator
import com.amazon.tv.leanbacklauncher.animation.LauncherPauseAnimator
import com.amazon.tv.leanbacklauncher.animation.LauncherReturnAnimator
import com.amazon.tv.leanbacklauncher.animation.MassSlideAnimator
import com.amazon.tv.leanbacklauncher.animation.NotificationLaunchAnimator
import com.amazon.tv.leanbacklauncher.animation.ParticipatesInLaunchAnimation
import com.amazon.tv.leanbacklauncher.apps.AppsManager.Companion.getInstance
import com.amazon.tv.leanbacklauncher.apps.BannerView
import com.amazon.tv.leanbacklauncher.apps.OnEditModeChangedListener
import com.amazon.tv.leanbacklauncher.clock.ClockView
import com.amazon.tv.leanbacklauncher.notifications.HomeScreenView
import com.amazon.tv.leanbacklauncher.notifications.NotificationCardView
import com.amazon.tv.leanbacklauncher.notifications.NotificationRowView
import com.amazon.tv.leanbacklauncher.notifications.NotificationRowView.NotificationRowListener
import com.amazon.tv.leanbacklauncher.notifications.NotificationsAdapter
import com.amazon.tv.leanbacklauncher.settings.LegacyHomeScreenSettingsActivity
import com.amazon.tv.leanbacklauncher.settings.SettingsActivity
import com.amazon.tv.leanbacklauncher.util.Partner
import com.amazon.tv.leanbacklauncher.util.Permission
import com.amazon.tv.leanbacklauncher.util.Util
import com.amazon.tv.leanbacklauncher.util.breath
import com.amazon.tv.leanbacklauncher.viewmodel.SearchViewModel
import com.amazon.tv.leanbacklauncher.wallpaper.LauncherWallpaper
import com.amazon.tv.leanbacklauncher.wallpaper.WallpaperInstaller
import com.amazon.tv.leanbacklauncher.widget.EditModeView
import com.amazon.tv.leanbacklauncher.widget.EditModeView.OnEditModeUninstallPressedListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.PrintWriter
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), OnEditModeChangedListener,
    OnEditModeUninstallPressedListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val FIRST_POSITION = 0
        const val PERMISSIONS_REQUEST_LOCATION = 99
        val JSONFILE = LauncherApp.context.cacheDir?.absolutePath + "/weather.json"

        fun isMediaKey(keyCode: Int): Boolean {
            return when (keyCode) {
                KeyEvent.KEYCODE_HEADSETHOOK,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_STOP,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MUTE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_RECORD -> true
                else -> false
            }
        }

        private fun getBoundsOnScreen(v: View, epicenter: Rect) {
            val location = IntArray(2)
            v.getLocationOnScreen(location)
            epicenter.left = location[0]
            epicenter.top = location[1]
            epicenter.right = epicenter.left + (v.width.toFloat() * v.scaleX).roundToInt()
            epicenter.bottom = epicenter.top + (v.height.toFloat() * v.scaleY).roundToInt()
        }
    }

    // ViewModel
    private val searchViewModel: SearchViewModel by viewModels()

    // Core components
    private val mIdleListeners = mutableListOf<IdleListener>()
    private val mNotifListener = NotificationListenerImpl()
    private val mPackageReplacedReceiver = PackageReplacedReceiver()
    private val mHomeRefreshReceiver = HomeRefreshReceiver()

    private var idleJob: Job? = null
    private var resetJob: Job? = null
    private var notificationStateJob: Job? = null
    private var uiVisibleJob: Job? = null
    private var widgetJob: Job? = null
    private var launchPointJob: Job? = null

    // Views
    var editModeView: EditModeView? = null
        private set
    var wallpaperView: LauncherWallpaper? = null
        private set
    private var mListView: VerticalGridView? = null
    private var mHomeScreenView: HomeScreenView? = null
    private var mNotificationsView: NotificationRowView? = null
    private var mAppWidgetHostView: AppWidgetHostView? = null

    // Adapters
    var homeAdapter: HomeScreenAdapter? = null
        private set
    private var mRecommendationsAdapter: NotificationsAdapter? = null

    // Services
    private var mAppWidgetHost: AppWidgetHost? = null
    private var mAppWidgetManager: AppWidgetManager? = null
    private var mContentResolver: ContentResolver? = null
    private var mAccessibilityManager: AccessibilityManager? = null
    private var mScrollManager: HomeScrollManager? = null

    // State variables
    var isInEditMode = false
        private set
    private var mDelayFirstRecommendationsVisible = true
    private var mFadeDismissAndSummonAnimations = false
    private var mIsIdle = false
    private var mKeepUiReset = false
    private var mUserInteracted = false
    private var mShyMode = false
    private var mStartingEditMode = false
    private var mUninstallRequested = false
    private var mResetAfterIdleEnabled = false

    private val mIdlePeriod: Int by lazy {
        resources.getInteger(R.integer.idle_period)
    }
    private val mResetPeriod: Int by lazy {
        resources.getInteger(R.integer.reset_period)
    }

    // Animations
    private val mEditModeAnimation = AnimatorLifecycle()
    private val mLaunchAnimation = AnimatorLifecycle()
    private val mPauseAnimation = AnimatorLifecycle()

    private val mMoveTaskToBack = Runnable {
        if (!moveTaskToBack(true)) {
            mLaunchAnimation.reset()
        }
    }

    private val mRefreshHomeAdapter = Runnable {
        homeAdapter?.refreshAdapterData()
    }

    // Activity Result Launchers
    private val uninstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        mUninstallRequested = false
        when (result.resultCode) {
            RESULT_OK -> editModeView?.uninstallComplete()
            1 -> editModeView?.uninstallFailure()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Handle result if needed
    }

    interface IdleListener {
        fun onIdleStateChange(z: Boolean)
        fun onVisibilityChange(z: Boolean)
    }

    // Inner classes
    private inner class NotificationListenerImpl : NotificationRowListener {
        private val mSelectFirstRecommendationRunnable = Runnable {
            mNotificationsView?.takeIf { (it.adapter?.itemCount ?: 0) > 0 }
                ?.setSelectedPositionSmooth(FIRST_POSITION)
        }

        override fun onBackgroundImageChanged(imageUri: String?, signature: String?) {
            wallpaperView?.onBackgroundImageChanged(imageUri, signature)
        }

        override fun onSelectedRecommendationChanged(position: Int) {
            if (mKeepUiReset && mAccessibilityManager?.isEnabled != true && position > FIRST_POSITION) {
                lifecycleScope.launch {
                    mSelectFirstRecommendationRunnable.run()
                }
            }
        }
    }

    private inner class PackageReplacedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.data?.toString()?.takeIf {
                it.contains("${context?.packageName}.recommendations")
            }?.let {
                Log.d(TAG, "Recommendations Service updated, reconnecting.")
                homeAdapter?.onReconnectToRecommendationsService()
            }
        }
    }

    private inner class HomeRefreshReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getBooleanExtra("RefreshHome", false) == true) {
                Log.d(TAG, "RESTART HOME")
                recreate()
            }
        }
    }

    // Coroutine-based message handling
    private fun sendIdleMessage(idle: Boolean) {
        mIsIdle = idle
        mIdleListeners.forEach { it.onIdleStateChange(mIsIdle) }
    }

    private fun scheduleIdleCheck() {
        idleJob?.cancel()
        idleJob = lifecycleScope.launch {
            delay(mIdlePeriod.toLong())
            sendIdleMessage(true)
        }
    }

    private fun scheduleResetCheck() {
        resetJob?.cancel()
        resetJob = lifecycleScope.launch {
            delay(mResetPeriod.toLong())
            if (mResetAfterIdleEnabled) {
                mKeepUiReset = true
                resetLauncherState(true)
            }
        }
    }

    private fun scheduleNotificationStateUpdate(state: Int) {
        notificationStateJob?.cancel()
        notificationStateJob = lifecycleScope.launch {
            delay(500)
            onNotificationRowStateUpdate(state)
        }
    }

    private fun scheduleUiVisible() {
        uiVisibleJob?.cancel()
        uiVisibleJob = lifecycleScope.launch {
            delay(1500)
            homeAdapter?.onUiVisible()
        }
    }

    private fun scheduleWidgetRefresh() {
        widgetJob?.cancel()
        widgetJob = lifecycleScope.launch {
            addWidget(true)
        }
    }

    private fun scheduleLaunchPointCheck() {
        launchPointJob?.cancel()
        launchPointJob = lifecycleScope.launch {
            delay(2000)
            checkLaunchPointPositions()
        }
    }

    private fun cancelAllJobs() {
        idleJob?.cancel()
        resetJob?.cancel()
        notificationStateJob?.cancel()
        uiVisibleJob?.cancel()
        widgetJob?.cancel()
        launchPointJob?.cancel()
    }

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mContentResolver = contentResolver

        if (mRecommendationsAdapter == null) {
            mRecommendationsAdapter = NotificationsAdapter(this)
        }
        val appContext = applicationContext
        setContentView(R.layout.activity_main)

        // Observe ViewModel
        observeViewModel()

        if (Partner.get(this).showLiveTvOnStartUp && checkFirstRunAfterBoot()) {
            val tvIntent = Intent("android.intent.action.VIEW", TvContract.buildChannelUri(0))
            tvIntent.putExtra("com.google.android.leanbacklauncher.extra.TV_APP_ON_BOOT", true)
            if (packageManager.queryIntentActivities(tvIntent, 1).isNotEmpty()) {
                startActivity(tvIntent)
                finish()
            }
        }

        // android O fix bug orientation
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        // overlay permissions request on M+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
                try {
                    overlayPermissionLauncher.launch(intent)
                } catch (_: Exception) {
                }
            }
        }

        // network monitor (request from HomeScreenAdapter)
        Permission.isLocationPermissionGranted(this)

        editModeView = findViewById<EditModeView?>(R.id.edit_mode_view)?.apply {
            setUninstallListener(this@MainActivity)
        }

        wallpaperView = findViewById(R.id.background_container)
        mAppWidgetManager = AppWidgetManager.getInstance(appContext)

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS))
            mAppWidgetHost = AppWidgetHost(this, 123)

        mListView = findViewById(R.id.main_list_view)
        mListView?.apply {
            setHasFixedSize(true)
            windowAlignment = BaseGridView.WINDOW_ALIGN_LOW_EDGE
            windowAlignmentOffset =
                resources.getDimensionPixelOffset(R.dimen.home_screen_selected_row_alignment)
            windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
            itemAlignmentOffset = 0
            itemAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED

            mScrollManager = HomeScrollManager(this@MainActivity, this).apply {
                addHomeScrollListener(wallpaperView!!)
            }
            homeAdapter = HomeScreenAdapter(
                this@MainActivity,
                mScrollManager!!,
                mRecommendationsAdapter,
                editModeView!!
            ).apply {
                setOnEditModeChangedListener(this@MainActivity)
            }
            setItemViewCacheSize(homeAdapter!!.itemCount)
            adapter = homeAdapter

            val notifIndex = homeAdapter?.getRowIndex(1) // RowType.NOTIFICATIONS
            if (notifIndex != null && notifIndex != -1) {
                selectedPosition = notifIndex
            }
            val rAdapter = homeAdapter?.recommendationsAdapter?.apply {
                addIdleListener(this)
            }
            setAnimateChildLayout(false)
            setOnChildViewHolderSelectedListener(object : OnChildViewHolderSelectedListener() {
                override fun onChildViewHolderSelected(
                    parent: RecyclerView,
                    child: RecyclerView.ViewHolder?,
                    position: Int,
                    subposition: Int
                ) {
                    homeAdapter?.onChildViewHolderSelected(parent, child, position)
                }
            })
            setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View, child: View) {
                    var tag = 0
                    if (child.tag is Int) {
                        tag = child.tag as Int
                    }
                    when (tag) {
                        0 -> {
                            if (child is SearchOrbView) {
                                child.setLaunchListener(object : SearchLaunchListener {
                                    override fun onSearchLaunched() {
                                        setShyMode(shy = true, changeWallpaper = true)
                                    }
                                })
                            }
                            addWidget(false)
                        }

                        1, 2 -> {
                            mHomeScreenView = child.findViewById(R.id.home_screen_messaging)
                            mHomeScreenView?.let {
                                val homeScreenMessaging = it.homeScreenMessaging
                                if (tag == 1) {
                                    rAdapter?.setNotificationRowViewFlipper(homeScreenMessaging)
                                    mNotificationsView = it.notificationRow
                                    mNotificationsView?.setListener(mNotifListener)
                                }
                                homeScreenMessaging.listener = { state ->
                                    scheduleNotificationStateUpdate(state)
                                    if (state == 0 && mDelayFirstRecommendationsVisible) {
                                        mDelayFirstRecommendationsVisible = false
                                        scheduleUiVisible()
                                    }
                                }
                            }
                        }
                    }
                    if (child is IdleListener && !mIdleListeners.contains(child)) {
                        addIdleListener(child as IdleListener)
                    }
                }

                override fun onChildViewRemoved(parent: View, child: View) {}
            })
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    mScrollManager?.onScrolled(dy, currentScrollPos)
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    mScrollManager?.onScrollStateChanged(newState)
                }
            })
        }

        mShyMode = true
        setShyMode(shy = !mShyMode, changeWallpaper = true)

        mFadeDismissAndSummonAnimations = resources.getBoolean(R.bool.app_launch_animation_fade)
        mKeepUiReset = true
        homeAdapter?.onInitUi()

        // register package change receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mPackageReplacedReceiver, filter, RECEIVER_EXPORTED)
            registerReceiver(
                mHomeRefreshReceiver,
                IntentFilter(this.javaClass.name),
                RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(mPackageReplacedReceiver, filter)
            registerReceiver(mHomeRefreshReceiver, IntentFilter(this.javaClass.name))
        }

        // start notification listener monitor
        if (RowPreferences.areRecommendationsEnabled(this) && LauncherApp.inForeground)
            startService(Intent(this, NotificationListenerMonitor::class.java))

        // fix int options migrate
        RowPreferences.fixRowPrefs()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    searchViewModel.searchIcon.collect { icon ->
                        homeAdapter?.onSearchIconUpdate(icon)
                    }
                }
                launch {
                    searchViewModel.suggestions.collect { suggestions ->
                        homeAdapter?.onSuggestionsUpdate(suggestions)
                    }
                }
            }
        }
    }

    public override fun onDestroy() {
        if (BuildConfig.DEBUG) Log.d(TAG, "onDestroy()")
        cancelAllJobs()
        super.onDestroy()
        homeAdapter?.let {
            it.onStopUi()
            it.unregisterReceivers()
        }
        getInstance(applicationContext)?.onDestroy()
        try {
            unregisterReceiver(mPackageReplacedReceiver)
            unregisterReceiver(mHomeRefreshReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receivers", e)
        }
    }

    override fun onUserInteraction() {
        resetJob?.cancel()
        mKeepUiReset = false
        if (hasWindowFocus()) {
            idleJob?.cancel()
            mUserInteracted = true
            if (mIsIdle) {
                sendIdleMessage(false)
            }
            scheduleIdleCheck()
        }
        scheduleResetCheck()
    }

    private fun addIdleListener(listener: IdleListener) {
        mIdleListeners.add(listener)
        listener.onVisibilityChange(true)
        listener.onIdleStateChange(mIsIdle)
    }

    override fun onBackPressed() {
        when {
            isInEditMode -> {
                editModeView?.onBackPressed()
            }
            mLaunchAnimation.isRunning -> {
                mLaunchAnimation.cancel()
            }
            mLaunchAnimation.isPrimed -> {
                mLaunchAnimation.reset()
            }
            else -> {
                if (mLaunchAnimation.isFinished) {
                    mLaunchAnimation.reset()
                }
                dismissLauncher()
            }
        }
    }

    override fun onEditModeChanged(z: Boolean) {
        if (isInEditMode == z) {
            return
        }
        if (mAccessibilityManager?.isEnabled == true) {
            setEditMode(editMode = z, useAnimation = false)
        } else {
            setEditMode(editMode = z, useAnimation = true)
        }
    }

    override fun onUninstallPressed(packageName: String?) {
        if (packageName != null && !mUninstallRequested) {
            mUninstallRequested = true
            val uninstallIntent = Intent(
                Intent.ACTION_UNINSTALL_PACKAGE,
                "package:$packageName".toUri()
            ).apply {
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            uninstallLauncher.launch(uninstallIntent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Agree location permission")
                    recreate()
                } else {
                    Log.i(TAG, "Not agree location permission")
                    LauncherApp.toast(R.string.location_note, true)
                }
            }
        }
    }

    private fun TextView.setupMarquee() {
        ellipsize = TextUtils.TruncateAt.MARQUEE
        isSingleLine = true
        marqueeRepeatLimit = -1
        isSelected = true
        isFocusableInTouchMode = false
        isFocusable = false
    }

    private fun setShyMode(shy: Boolean, changeWallpaper: Boolean): Boolean {
        var changed = false
        if (mShyMode != shy) {
            mShyMode = shy
            changed = true
            if (mShyMode) {
                convertFromTranslucent()
            } else {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    convertToTranslucent()
                }
            }
        }
        if (changeWallpaper && wallpaperView?.shynessMode != shy) {
            wallpaperView?.shynessMode = mShyMode
            if (mShyMode && mNotificationsView != null) {
                mNotificationsView?.refreshSelectedBackground()
            }
        }
        return changed
    }

    private fun convertFromTranslucent() {
        try {
            val convertFromTranslucent =
                Activity::class.java.getDeclaredMethod("convertFromTranslucent")
            convertFromTranslucent.isAccessible = true
            convertFromTranslucent.invoke(this@MainActivity)
        } catch (_: Throwable) {
        }
    }

    private fun convertToTranslucent() {
        try {
            var translucentConversionListenerClazz: Class<*>? = null
            for (clazz in Activity::class.java.declaredClasses) {
                if (clazz.simpleName.contains("TranslucentConversionListener")) {
                    translucentConversionListenerClazz = clazz
                }
            }
            val convertToTranslucent = Activity::class.java.getDeclaredMethod(
                "convertToTranslucent",
                translucentConversionListenerClazz,
                ActivityOptions::class.java
            )
            convertToTranslucent.isAccessible = true
            convertToTranslucent.invoke(this@MainActivity, null, null)
        } catch (_: Throwable) {
        }
    }

    private fun dismissLauncher(): Boolean {
        if (mShyMode) {
            return false
        }
        mLaunchAnimation.init(
            LauncherDismissAnimator(
                mListView!!,
                mFadeDismissAndSummonAnimations,
                homeAdapter!!.rowHeaders as Array<View>
            ), mMoveTaskToBack, 0.toByte()
        )
        mLaunchAnimation.start()
        return true
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        var exitingEditMode = false
        if (isInEditMode) {
            if (Util.isInTouchExploration(applicationContext)) {
                setTitle(R.string.app_label)
            }
            setEditMode(editMode = false, useAnimation = true)
            exitingEditMode = true
        }
        if (mLaunchAnimation.isRunning) {
            mLaunchAnimation.cancel()
            return
        }
        if (mLaunchAnimation.isPrimed) {
            mLaunchAnimation.reset()
        }
        if (mLaunchAnimation.isFinished) {
            mLaunchAnimation.reset()
        }
        if (!exitingEditMode) {
            intent?.extras?.let {
                if (it.getBoolean("extra_start_customize_apps")) {
                    startEditMode(3)
                } else if (it.getBoolean("extra_start_customize_games")) {
                    startEditMode(4)
                }
            }
            if (!mStartingEditMode) {
                if (!hasWindowFocus() || intent?.getBooleanExtra(
                        "com.android.systemui.recents.tv.RecentsTvActivity.RECENTS_HOME_INTENT_EXTRA",
                        false
                    ) == true
                ) {
                    if (!mLaunchAnimation.isScheduled) {
                        resetLauncherState(false)
                        mLaunchAnimation.init(
                            MassSlideAnimator.Builder(mListView!!)
                                .setDirection(MassSlideAnimator.Direction.SLIDE_IN)
                                .setFade(mFadeDismissAndSummonAnimations)
                                .build(), mRefreshHomeAdapter, 32.toByte()
                        )
                    }
                } else if (!dismissLauncher()) {
                    resetLauncherState(true)
                }
            }
        } else if (!mLaunchAnimation.isInitialized && !mLaunchAnimation.isScheduled) {
            resetLauncherState(false)
            mLaunchAnimation.init(
                MassSlideAnimator.Builder(mListView!!)
                    .setDirection(MassSlideAnimator.Direction.SLIDE_IN)
                    .setFade(mFadeDismissAndSummonAnimations)
                    .build(), mRefreshHomeAdapter, 32.toByte()
            )
        }
    }

    private fun startEditMode(rowType: Int) {
        if (Util.isInTouchExploration(applicationContext)) {
            setTitle(if (rowType == 3) R.string.title_app_edit_mode else R.string.title_game_edit_mode)
        }
        mStartingEditMode = true
        homeAdapter?.resetRowPositions(false)
        mLaunchAnimation.cancel()
        mListView?.selectedPosition = homeAdapter!!.getRowIndex(rowType)
        homeAdapter?.prepareEditMode(rowType)
    }

    private fun resetLauncherState(smooth: Boolean) {
        if (BuildConfig.DEBUG) Log.d(TAG, "resetLauncherState(smooth:$smooth)")
        mScrollManager?.onScrolled(0, 0)
        mUserInteracted = false
        homeAdapter?.resetRowPositions(smooth)

        if (isInEditMode) {
            setEditMode(editMode = false, useAnimation = smooth)
        }
        val currIndex = mListView!!.selectedPosition
        var notifIndex = homeAdapter!!.getRowIndex(1) // 1 - Recommendations row
        mListView?.adapter?.let {
            notifIndex = (it.itemCount - 1).coerceAtMost(notifIndex)
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "currIndex:$currIndex, notifIndex:$notifIndex")
        if (notifIndex != -1 && currIndex != notifIndex) {
            if (BuildConfig.DEBUG) Log.d(
                TAG,
                "resetLauncherState -> set focus to Recommendations row"
            )
            if (smooth) {
                mListView?.setSelectedPositionSmooth(notifIndex)
            } else {
                mListView?.selectedPosition = notifIndex
                val focusedChild = mListView?.focusedChild
                focusedChild?.let { child ->
                    val focusedPosition = mListView?.getChildAdapterPosition(child)
                    if (focusedPosition == notifIndex) {
                        child.clearFocus()
                    }
                }
            }
            if (!(mShyMode || mNotificationsView == null)) {
                mNotificationsView?.setIgnoreNextActivateBackgroundChange()
            }
        } else if (notifIndex == -1) {
            val rowTypes = intArrayOf(7, 9, 8, 4, 3)
            for (type in rowTypes) {
                var rowIndex = homeAdapter?.getRowIndex(type) ?: -1
                mListView?.adapter?.let {
                    rowIndex = (it.itemCount - 1).coerceAtMost(rowIndex)
                }
                if (rowIndex != -1) {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "resetLauncherState -> set focus to ${RowType.fromRowCode(type)} row"
                    )
                    if (smooth) {
                        mListView?.setSelectedPositionSmooth(rowIndex)
                    } else {
                        mListView?.selectedPosition = rowIndex
                    }
                    break
                }
            }
        }
        mLaunchAnimation.cancel()
    }

    private val isBackgroundVisibleBehind: Boolean
        get() {
            try {
                val isBackgroundVisibleBehind =
                    Activity::class.java.getDeclaredMethod("isBackgroundVisibleBehind")
                isBackgroundVisibleBehind.isAccessible = true
                return isBackgroundVisibleBehind.invoke(this@MainActivity) as Boolean
            } catch (_: Throwable) {
            }
            return false
        }

    override fun onStart() {
        super.onStart()
        mResetAfterIdleEnabled = false
        try {
            mAppWidgetHost?.startListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setShyMode(!isBackgroundVisibleBehind, true)

        wallpaperView?.resetBackground()
        homeAdapter?.refreshAdapterData()
        if (mKeepUiReset) {
            resetLauncherState(false)
        }

        if (!mStartingEditMode) {
            if (!mLaunchAnimation.isInitialized) {
                mLaunchAnimation.init(
                    LauncherReturnAnimator(
                        mListView!!,
                        mLaunchAnimation.lastKnownEpicenter,
                        homeAdapter!!.rowHeaders as Array<View>,
                        mHomeScreenView
                    ), mRefreshHomeAdapter, 32.toByte()
                )
            }
            mLaunchAnimation.schedule()
        }
        mStartingEditMode = false
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {}

    override fun onResume() {
        var forceResort = true

        super.onResume()

        val shyChanged = setShyMode(!isBackgroundVisibleBehind, true)

        if (!getInstance(applicationContext)!!.checkIfResortingIsNeeded() || isInEditMode) {
            forceResort = false
        }
        homeAdapter?.sortRowsIfNeeded(forceResort)
        WallpaperInstaller.getInstance(this)?.installWallpaperIfNeeded()
        wallpaperView?.shynessMode = mShyMode
        if (shyChanged) {
            wallpaperView?.resetBackground()
        }
        if (mShyMode) {
            mNotificationsView?.refreshSelectedBackground()
        }

        scheduleWidgetRefresh()

        homeAdapter?.animateSearchIn()
        for (i in mIdleListeners.indices) {
            mIdleListeners[i].onVisibilityChange(true)
        }
        mResetAfterIdleEnabled = true

        resetJob?.cancel()
        idleJob?.cancel()

        if (mIsIdle) {
            sendIdleMessage(false)
        } else {
            scheduleIdleCheck()
        }
        scheduleResetCheck()
        scheduleLaunchPointCheck()

        if (mLaunchAnimation.isFinished) {
            mLaunchAnimation.reset()
        }
        if (mLaunchAnimation.isInitialized) {
            mLaunchAnimation.reset()
        }
        if (mLaunchAnimation.isScheduled) {
            primeAnimationAfterLayout()
        }
        mPauseAnimation.reset()

        if (isInEditMode) {
            if (mEditModeAnimation.isInitialized)
                mEditModeAnimation.reset()
            mEditModeAnimation.init(
                EditModeMassFadeAnimator(this, EditMode.ENTER),
                null,
                0.toByte()
            )
            mEditModeAnimation.start()
        }
        mUninstallRequested = false

        overridePendingTransition(R.anim.home_fade_in_top, R.anim.home_fade_out_bottom)

        if (!(homeAdapter == null || homeAdapter!!.isUiVisible || mDelayFirstRecommendationsVisible)) {
            homeAdapter?.onUiVisible()
        }
    }

    override fun onEnterAnimationComplete() {
        if (mLaunchAnimation.isScheduled || mLaunchAnimation.isPrimed) {
            mLaunchAnimation.start()
        }
    }

    override fun onPause() {
        super.onPause()
        mResetAfterIdleEnabled = false
        mLaunchAnimation.cancel()
        idleJob?.cancel()
        widgetJob?.cancel()
        launchPointJob?.cancel()

        for (i in mIdleListeners.indices) {
            mIdleListeners[i].onVisibilityChange(false)
        }
        if (isInEditMode) {
            if (mEditModeAnimation.isInitialized)
                mEditModeAnimation.reset()
            mEditModeAnimation.init(
                EditModeMassFadeAnimator(this, EditMode.EXIT),
                null,
                0.toByte()
            )
            mEditModeAnimation.start()
        }
        mPauseAnimation.init(LauncherPauseAnimator(mListView!!), null, 0.toByte())
        mPauseAnimation.start()
        if (homeAdapter != null && homeAdapter!!.isUiVisible) {
            homeAdapter?.onUiInvisible()
            mDelayFirstRecommendationsVisible = false
        }
    }

    override fun onStop() {
        mResetAfterIdleEnabled = true
        try {
            mAppWidgetHost?.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cancelAllJobs()
        scheduleResetCheck()

        if (isInEditMode) {
            setEditMode(editMode = false, useAnimation = false)
        }
        setShyMode(shy = false, changeWallpaper = false)

        homeAdapter?.sortRowsIfNeeded(false)
        mLaunchAnimation.reset()
        super.onStop()
    }

    override fun dump(
        prefix: String,
        fd: FileDescriptor?,
        writer: PrintWriter,
        args: Array<String>?
    ) {
        super.dump(prefix, fd, writer, args)
        getInstance(applicationContext)?.dump(prefix, writer)
        mLaunchAnimation.dump(prefix, writer, mListView)
        homeAdapter?.dump(prefix, writer)
    }

    private val currentScrollPos: Int
        get() {
            var position = 0
            var topView = -1
            var rowIndex = 0
            while (rowIndex < mListView!!.childCount) {
                val child = mListView!!.getChildAt(rowIndex)
                if (child == null || child.top > 0) {
                    rowIndex++
                } else {
                    topView = mListView!!.getChildAdapterPosition(child)
                    if (child.measuredHeight > 0) {
                        position = (homeAdapter!!.getScrollOffset(topView)
                            .toFloat() * (abs(child.top).toFloat() / child.measuredHeight.toFloat()) * -1.0f).toInt()
                    }
                    topView--
                    while (topView >= 0) {
                        position -= homeAdapter!!.getScrollOffset(topView)
                        topView--
                    }
                    return position
                }
            }
            return 0
        }

    private fun onNotificationRowStateUpdate(state: Int) {
        if (state == 1 || state == 2) {
            if (!mUserInteracted) {
                val searchIndex = homeAdapter!!.getRowIndex(0)
                if (searchIndex != -1) {
                    mListView?.selectedPosition = searchIndex
                    mListView?.getChildAt(searchIndex)?.requestFocus()
                }
            }
        } else if (state == 0 && mListView!!.selectedPosition <= homeAdapter!!.getRowIndex(1) && mNotificationsView!!.isNotEmpty()) {
            mNotificationsView?.selectedPosition = 0
            mNotificationsView?.getChildAt(0)?.requestFocus()
        }
    }

    override fun onSearchRequested(): Boolean {
        setShyMode(shy = true, changeWallpaper = true)
        return super.onSearchRequested()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (mLaunchAnimation.isPrimed || mLaunchAnimation.isRunning || mEditModeAnimation.isPrimed || mEditModeAnimation.isRunning) {
            when (keyCode) {
                KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK -> super.onKeyDown(keyCode, event)
                else -> true
            }
        } else if (mShyMode || !isMediaKey(event.keyCode)) {
            super.onKeyDown(keyCode, event)
        } else {
            true
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
            val selectItem = mListView!!.focusedChild
            if (selectItem is ActiveFrame) {
                val v = selectItem.mRow
                val child = v?.focusedChild
                if (child is BannerView) {
                    val holder = child.viewHolder
                    if (holder != null) {
                        val pkg = holder.packageName
                        val intent = Intent(this, AppInfoActivity::class.java)
                        val bundle = Bundle()
                        bundle.putString("pkg", pkg)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    }
                }
            }
            return true
        }
        return if (mShyMode || !isMediaKey(event.keyCode)) {
            super.onKeyUp(keyCode, event)
        } else when (keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                setShyMode(shy = true, changeWallpaper = true)
                true
            }
            else -> true
        }
    }

    private fun addWidget(refresh: Boolean) {
        val wrapper: ViewGroup? = findViewById<View>(R.id.widget_wrapper) as? LinearLayout?
        wrapper?.let { wrap ->
            if (refresh || mAppWidgetHostView == null) {
                wrap.removeAllViews()
                var success = false
                var appWidgetId = Util.getWidgetId(this)
                val appWidgetComp = Partner.get(this).widgetComponentName
                if (appWidgetComp != null) {
                    for (appWidgetInfo in mAppWidgetManager!!.installedProviders) {
                        if (appWidgetComp == appWidgetInfo.provider) {
                            success = appWidgetId != 0
                            if (success && appWidgetComp != Util.getWidgetComponentName(this)) {
                                clearWidget(appWidgetId)
                                success = false
                            }
                            if (!success) {
                                val width = resources.getDimension(R.dimen.widget_width).toInt()
                                val height =
                                    resources.getDimension(R.dimen.widget_height).toInt()
                                val options = Bundle()
                                options.putInt("appWidgetMinWidth", width)
                                options.putInt("appWidgetMaxWidth", width)
                                options.putInt("appWidgetMinHeight", height)
                                options.putInt("appWidgetMaxHeight", height)
                                appWidgetId = mAppWidgetHost?.allocateAppWidgetId() ?: 0
                                success = mAppWidgetManager?.bindAppWidgetIdIfAllowed(
                                    appWidgetId,
                                    appWidgetInfo.provider,
                                    options
                                ) == true
                            }
                            if (success) {
                                mAppWidgetHostView =
                                    mAppWidgetHost?.createView(this, appWidgetId, appWidgetInfo)
                                mAppWidgetHostView?.setAppWidget(appWidgetId, appWidgetInfo)
                                wrap.addView(mAppWidgetHostView)
                                Util.setWidget(this, appWidgetId, appWidgetInfo.provider)
                            }
                        }
                    }
                }
                if (!success) {
                    clearWidget(appWidgetId)
                    // clock
                    wrap.addView(LayoutInflater.from(this).inflate(R.layout.clock, wrap, false))
                    val typeface = ResourcesCompat.getFont(this, R.font.sfuidisplay_thin)
                    val clockView: TextView? = findViewById<View>(R.id.clock) as ClockView?
                    typeface?.let {
                        clockView?.typeface = typeface
                    }
                    // settings
                    val settingsVG: ViewGroup? =
                        findViewById<View>(R.id.settings) as LinearLayout?
                    settingsVG?.let { group ->
                        val sel = findViewById<ImageView>(R.id.settings_selection_circle)
                        sel?.setColorFilter(
                            RowPreferences.getFrameColor(this),
                            PorterDuff.Mode.SRC_ATOP
                        )
                        val icon = findViewById<ImageView>(R.id.settings_icon)
                        group.setOnClickListener {
                            startSettings()
                        }
                        group.setOnFocusChangeListener { _, hasFocus ->
                            if (hasFocus) {
                                sel?.alpha = 1.0f
                                icon?.clearAnimation()
                                icon?.alpha = 1.0f
                            } else {
                                sel?.animate()?.apply {
                                    interpolator = LinearInterpolator()
                                    duration = 500
                                    alpha(0.0f)
                                    start()
                                }
                                icon?.alpha = 1.0f
                                icon?.breath()
                            }
                        }
                        sel?.alpha = 0.0f
                        icon?.alpha = 0.0f
                        icon?.animate()?.apply {
                            interpolator = LinearInterpolator()
                            duration = 500
                            alpha(1.0f)
                            start()
                        }
                        icon?.breath()
                    }
                    return
                }
                return
            }
            val parent = mAppWidgetHostView!!.parent as ViewGroup
            if (parent !== wrap) {
                parent.removeView(mAppWidgetHostView)
                wrap.removeAllViews()
                wrap.addView(mAppWidgetHostView)
            }
        }
    }

    private fun startSettings() {
        if (applicationContext.resources.getBoolean(R.bool.full_screen_settings_enabled)) {
            val intent = Intent(this@MainActivity, LegacyHomeScreenSettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        } else if (applicationContext.resources.getBoolean(R.bool.side_panel_settings_enabled)) {
            val intent = Intent(this@MainActivity, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }
    }

    private fun clearWidget(appWidgetId: Int) {
        if (appWidgetId != 0) {
            mAppWidgetHost?.deleteAppWidgetId(appWidgetId)
        }
        Util.clearWidget(this)
    }

    val editModeWallpaper: View
        get() = findViewById(R.id.edit_mode_background)

    private fun setEditMode(editMode: Boolean, useAnimation: Boolean) {
        var alpha = 1.0f
        mEditModeAnimation.reset()
        if (useAnimation) {
            mEditModeAnimation.init(
                EditModeMassFadeAnimator(
                    this,
                    if (editMode) EditMode.ENTER else EditMode.EXIT
                ), null, 0.toByte()
            )
            mEditModeAnimation.start()
        } else {
            val launcherWallpaper = wallpaperView
            val f: Float = if (editMode) {
                0.0f
            } else {
                1.0f
            }
            launcherWallpaper?.alpha = f
            val editModeWallpaper = editModeWallpaper
            if (!editMode) {
                alpha = 0.0f
            }
            editModeWallpaper.alpha = alpha
            editModeWallpaper.visibility = View.VISIBLE
            homeAdapter?.setRowAlphas(if (editMode) 0 else 1)
        }

        if (!editMode && isInEditMode) {
            for (i in 0 until mListView!!.childCount) {
                val activeFrame = mListView?.getChildAt(i)
                if (activeFrame is ActiveFrame) {
                    for (j in 0 until activeFrame.childCount) {
                        val activeItemsRow = activeFrame.getChildAt(j)
                        if (activeItemsRow is EditableAppsRowView) {
                            activeItemsRow.editMode = false
                        }
                    }
                }
            }
        }
        isInEditMode = editMode
    }

    @SuppressLint("WrongConstant")
    private fun checkFirstRunAfterBoot(): Boolean {
        val dummyIntent = Intent("android.intent.category.LEANBACK_LAUNCHER")
        dummyIntent.setClass(this, DummyActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            FLAG_NO_CREATE or FLAG_IMMUTABLE
        } else {
            FLAG_NO_CREATE
        }
        val firstRun = PendingIntent.getActivity(this, 0, dummyIntent, flags) == null
        if (firstRun) {
            val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                FLAG_IMMUTABLE
            } else {
                0
            }
            (getSystemService(ALARM_SERVICE) as AlarmManager)[AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 864000000000L] =
                PendingIntent.getActivity(this, 0, dummyIntent, pendingFlags)
        }
        return firstRun
    }

    fun beginLaunchAnimation(
        view: View,
        translucent: Boolean,
        color: Int,
        onCompleteCallback: Runnable
    ) {
        if (!mLaunchAnimation.isPrimed && !mLaunchAnimation.isRunning && !mLaunchAnimation.isFinished) {
            getBoundsOnScreen(view, mLaunchAnimation.lastKnownEpicenter)
            if (translucent) {
                onCompleteCallback.run()
                return
            }
            val animation: ForwardingAnimatorSet = if (view is NotificationCardView) {
                NotificationLaunchAnimator(
                    mListView!!,
                    view,
                    mLaunchAnimation.lastKnownEpicenter,
                    findViewById<View>(R.id.click_circle_layer) as ImageView,
                    color,
                    homeAdapter!!.rowHeaders as Array<View>,
                    mHomeScreenView
                )
            } else {
                LauncherLaunchAnimator(
                    mListView!!,
                    view,
                    mLaunchAnimation.lastKnownEpicenter,
                    findViewById<View>(R.id.click_circle_layer) as ImageView,
                    color,
                    homeAdapter!!.rowHeaders as Array<View>,
                    mHomeScreenView
                )
            }
            mLaunchAnimation.init(animation, onCompleteCallback, 0.toByte())
            mLaunchAnimation.start()
        }
    }

    val isLaunchAnimationInProgress: Boolean
        get() = mLaunchAnimation.isPrimed || mLaunchAnimation.isRunning

    val isEditAnimationInProgress: Boolean
        get() = mEditModeAnimation.isPrimed || mEditModeAnimation.isRunning

    fun includeInLaunchAnimation(target: View?) {
        mLaunchAnimation.include(target!!)
    }

    fun includeInEditAnimation(target: View?) {
        mEditModeAnimation.include(target!!)
    }

    fun excludeFromLaunchAnimation(target: View?) {
        mLaunchAnimation.exclude(target!!)
    }

    fun excludeFromEditAnimation(target: View?) {
        mEditModeAnimation.exclude(target!!)
    }

    fun setOnLaunchAnimationFinishedListener(l: OnAnimationFinishedListener?) {
        mLaunchAnimation.setOnAnimationFinishedListener({ l })
    }

    private fun primeAnimationAfterLayout() {
        mListView?.rootView?.viewTreeObserver?.addOnGlobalLayoutListener(object :
            OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                mListView?.rootView?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                if (mLaunchAnimation.isScheduled) {
                    mLaunchAnimation.prime()
                }
            }
        })
        mListView?.requestLayout()
    }

    private fun checkLaunchPointPositions() {
        if (!mLaunchAnimation.isRunning && checkViewHierarchy(mListView)) {
            mLaunchAnimation.reset()
        }
    }

    private fun checkViewHierarchy(view: View?): Boolean {
        if (view is ParticipatesInLaunchAnimation && view.translationY != 0.0f) {
            return true
        }
        if (view is ViewGroup) {
            val n = view.childCount
            for (i in 0 until n) {
                if (checkViewHierarchy(view.getChildAt(i))) {
                    return true
                }
            }
        }
        return false
    }
}