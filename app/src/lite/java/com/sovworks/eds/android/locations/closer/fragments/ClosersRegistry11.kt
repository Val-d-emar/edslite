package com.sovworks.eds.android.locations.closer.fragments

import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.Openable

object ClosersRegistry {
    fun getDefaultCloserForLocation(location: Location?): LocationCloserBaseFragment {
        return if (location is Openable)
            OpenableLocationCloserFragment()
        else
            LocationCloserBaseFragment()
    }
}
