package org.mlm.forbendlauncher.notifications

interface BlacklistListener {
    fun onPackageBlacklisted(str: String?)
    fun onPackageUnblacklisted(str: String?)
}