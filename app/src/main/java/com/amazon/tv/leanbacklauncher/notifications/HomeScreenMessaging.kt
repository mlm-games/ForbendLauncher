package com.amazon.tv.leanbacklauncher.notifications

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.lang.ref.WeakReference

class HomeScreenMessaging(private val homeScreenView: HomeScreenView) {

    fun interface ChangeListener {
        fun onStateChanged(state: Int)
    }

    var listener: ChangeListener? = null
    
    private var connected = true
    private var recommendationsVisible = false
    private var viewState = -1
    private var nextViewState = -1
    private var timeoutViewState = VIEW_STATE_TIMEOUT
    private var whenNextViewVisible = 0L
    
    private val timer = TimerHandler(this)

    private class TimerHandler(messaging: HomeScreenMessaging) : Handler(Looper.getMainLooper()) {
        private val parent = WeakReference(messaging)
        
        override fun handleMessage(msg: android.os.Message) {
            val messaging = parent.get() ?: return
            when (msg.what) {
                MSG_MIN_VISIBLE_TIMER -> messaging.minimumViewVisibleTimerTriggered()
                MSG_PREPARING_TIMEOUT -> messaging.preparingTimeoutTriggered()
            }
        }
    }

    private fun applyViewState(viewState: Int) {
        homeScreenView.flipToView(when (viewState) {
            VIEW_STATE_VISIBLE -> 0
            VIEW_STATE_PREPARING -> 1
            VIEW_STATE_DISABLED -> 2
            VIEW_STATE_TIMEOUT -> 3
            VIEW_STATE_NO_CONNECTION -> 4
            else -> -1
        })
        
        this.viewState = viewState
        
        val newState = when (viewState) {
            VIEW_STATE_VISIBLE -> STATE_VISIBLE
            VIEW_STATE_PREPARING -> STATE_DISABLED
            VIEW_STATE_DISABLED, VIEW_STATE_TIMEOUT, VIEW_STATE_NO_CONNECTION -> STATE_ERROR
            else -> -1
        }
        listener?.onStateChanged(newState)
    }

    private fun minimumViewVisibleTimerTriggered() {
        if (nextViewState != -1) {
            applyViewState(nextViewState)
            nextViewState = -1
            whenNextViewVisible = SystemClock.elapsedRealtime() + MIN_VIEW_VISIBLE_MS
        }
    }

    private fun preparingTimeoutTriggered() {
        applyViewState(timeoutViewState)
        nextViewState = -1
        whenNextViewVisible = SystemClock.elapsedRealtime() + MIN_VIEW_VISIBLE_MS
    }

    private fun setViewState(newViewState: Int) {
        stopPreparingTimeout()
        val now = SystemClock.elapsedRealtime()
        
        val resolvedViewState = if ((newViewState == VIEW_STATE_DISABLED || 
            newViewState == VIEW_STATE_TIMEOUT) && !connected) {
            VIEW_STATE_NO_CONNECTION
        } else newViewState

        if (now >= whenNextViewVisible || (viewState == VIEW_STATE_VISIBLE && resolvedViewState != VIEW_STATE_VISIBLE)) {
            nextViewState = -1
            timer.removeMessages(MSG_MIN_VISIBLE_TIMER)
            applyViewState(resolvedViewState)
            whenNextViewVisible = now + MIN_VIEW_VISIBLE_MS
        } else {
            if (nextViewState == -1) {
                timer.sendEmptyMessageDelayed(MSG_MIN_VISIBLE_TIMER, MIN_VIEW_VISIBLE_MS)
            }
            nextViewState = resolvedViewState
        }
    }

    private fun startPreparingTimeout(timeoutViewState: Int) {
        stopPreparingTimeout()
        this.timeoutViewState = timeoutViewState
        timer.sendEmptyMessageDelayed(MSG_PREPARING_TIMEOUT, PREPARING_TIMEOUT_MS)
    }

    private fun stopPreparingTimeout() {
        timer.removeMessages(MSG_PREPARING_TIMEOUT)
    }

    fun recommendationsUpdated(hasRecommendations: Boolean) {
        if (recommendationsVisible != hasRecommendations) {
            recommendationsVisible = hasRecommendations
            if (hasRecommendations) {
                setViewState(VIEW_STATE_VISIBLE)
            } else if (viewState != VIEW_STATE_PREPARING) {
                setViewState(VIEW_STATE_PREPARING)
                startPreparingTimeout(VIEW_STATE_TIMEOUT)
            }
        }
    }

    fun onClearRecommendations(reason: Int) {
        when (reason) {
            CLEAR_DISABLED -> {
                recommendationsVisible = false
                setViewState(VIEW_STATE_DISABLED)
            }
            CLEAR_PENDING_DISABLED -> {
                if (viewState != VIEW_STATE_PREPARING) {
                    resetToPreparing(VIEW_STATE_DISABLED)
                }
            }
            else -> {
                if (viewState != VIEW_STATE_PREPARING) {
                    resetToPreparing(VIEW_STATE_TIMEOUT)
                }
            }
        }
    }

    private fun resetToPreparing(timeoutViewState: Int) {
        recommendationsVisible = false
        setViewState(VIEW_STATE_PREPARING)
        startPreparingTimeout(timeoutViewState)
    }

    fun resetToPreparing() = resetToPreparing(VIEW_STATE_TIMEOUT)

    fun onConnectivityChange(connected: Boolean) {
        if (connected != this.connected) {
            this.connected = connected
            if (connected) {
                if (viewState == VIEW_STATE_NO_CONNECTION) {
                    setViewState(VIEW_STATE_PREPARING)
                    startPreparingTimeout(timeoutViewState)
                }
            } else if (viewState in listOf(VIEW_STATE_PREPARING, VIEW_STATE_DISABLED, VIEW_STATE_TIMEOUT)) {
                setViewState(VIEW_STATE_NO_CONNECTION)
            }
        }
    }

    companion object {
        const val STATE_VISIBLE = 0
        const val STATE_ERROR = 1
        const val STATE_DISABLED = 2
        
        private const val VIEW_STATE_VISIBLE = 0
        private const val VIEW_STATE_PREPARING = 1
        private const val VIEW_STATE_DISABLED = 2
        private const val VIEW_STATE_TIMEOUT = 3
        private const val VIEW_STATE_NO_CONNECTION = 4

        private const val CLEAR_DISABLED = 2
        private const val CLEAR_PENDING_DISABLED = 4

        private const val MSG_MIN_VISIBLE_TIMER = 0
        private const val MSG_PREPARING_TIMEOUT = 1
        
        private const val MIN_VIEW_VISIBLE_MS = 1000L
        private const val PREPARING_TIMEOUT_MS = 30000L
    }
}