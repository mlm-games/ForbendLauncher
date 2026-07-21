package org.mlm.forbendlauncher

import org.mlm.forbendlauncher.animation.ViewDimmer.DimState

interface DimmableItem {

    fun setDimState(dimState: DimState, z: Boolean)

}