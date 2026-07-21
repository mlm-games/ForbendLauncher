package org.mlm.forbendlauncher.apps

interface InstallingLaunchPointListener {
    fun onInstallingLaunchPointAdded(launchPoint: LaunchPoint?)
    fun onInstallingLaunchPointChanged(launchPoint: LaunchPoint?)
    fun onInstallingLaunchPointRemoved(launchPoint: LaunchPoint?, z: Boolean)
}