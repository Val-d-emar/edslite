package com.sovworks.eds.android.locations

import android.content.Context
import android.net.Uri
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.encfs.FS
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManagerBase
import com.sovworks.eds.settings.Settings

class EncFsLocation : EncFsLocationBase {
    constructor(
        uri: Uri,
        lm: LocationsManagerBase?,
        context: Context?,
        settings: Settings?
    ) : super(uri, lm, context, settings)

    constructor(location: Location, context: Context?) : this(
        location,
        null,
        context,
        UserSettings.getSettings(context)
    )

    constructor(
        containerLocation: Location,
        encFs: FS?,
        context: Context?,
        settings: Settings?
    ) : super(containerLocation, encFs, context, settings)

    constructor(sibling: EncFsLocationBase?) : super(sibling)

    override fun copy(): EncFsLocation? {
        return EncFsLocation(this)
    }
}
