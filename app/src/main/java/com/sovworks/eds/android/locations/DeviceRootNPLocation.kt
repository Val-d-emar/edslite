package com.sovworks.eds.android.locations

import android.content.Context
import android.net.Uri
import android.net.Uri.Builder
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.locations.DeviceBasedLocation
import com.sovworks.eds.settings.Settings

class DeviceRootNPLocation : DeviceBasedLocation {
    @JvmOverloads
    constructor(context: Context, currentPath: String? = "") : super(
        UserSettings.getSettings(
            context
        ), context.getString(R.string.device), null, currentPath
    ) {
        _context = context
    }

    constructor(context: Context, settings: Settings?, locationUri: Uri) : super(
        settings,
        locationUri
    ) {
        _context = context
    }

    override fun getLocationUri(): Uri {
        return makeFullUri().build()
    }

    override fun makeFullUri(): Builder {
        return super.makeFullUri().scheme(locationId)
    }

    override fun getId(): String {
        return locationId
    }

    override fun copy(): DeviceRootNPLocation {
        return DeviceRootNPLocation(_context, _currentPathString)
    }

    private val _context: Context

    companion object {
        const val locationId: String = "rootfsnp"
    }
}
