package org.mlm.forbendlauncher.recommendations

import android.content.Context
import org.mlm.forbendlauncher.service.RankerParameters
import org.mlm.forbendlauncher.service.RankerParametersFactory

internal class GservicesRankerParameters private constructor() :
    RankerParameters() {
    class Factory : RankerParametersFactory {
        override fun create(context: Context): RankerParameters {
            return GservicesRankerParameters()
        }
    }

    public override fun getVersionToken(): Any {
        return 0
    }

    public override fun getFloat(key: String, defaultValue: Float): Float {
        return defaultValue
    }

}