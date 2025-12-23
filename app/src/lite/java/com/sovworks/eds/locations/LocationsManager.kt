package com.sovworks.eds.locations

import android.content.Context
import com.sovworks.eds.settings.Settings

class LocationsManager(context: Context, settings: Settings) :
    LocationsManagerBase(context, settings) {
    companion object {
        fun isOpen(loc: Location?): Boolean {
            return loc !is Openable || loc.isOpen
        }

        fun isOpenableAndOpen(loc: Location?): Boolean {
            return loc is Openable && isOpen(loc)
        }
    }
}
