package org.mlm.forbendlauncher.widget

interface EditModeViewActionListener {
    fun onEditModeExitTriggered()
    fun onFocusLeavingEditModeLayer(i: Int)
    fun onPrepForUninstall(): String?
    fun onUninstallComplete()
    fun onUninstallFailure()
}