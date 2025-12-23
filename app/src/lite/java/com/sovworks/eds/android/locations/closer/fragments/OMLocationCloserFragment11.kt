package com.sovworks.eds.android.locations.closer.fragments

import android.content.Context
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.Openable

object OMLocationCloserFragment : OpenableLocationCloserFragment() {
    @Throws(Exception::class)
    fun unmountAndClose(context: Context, location: Location?, forceClose: Boolean) {
        closeLocation(context, (location as Openable?)!!, forceClose)
    }
}
