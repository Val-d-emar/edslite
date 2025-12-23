package com.sovworks.eds.android.locations

import android.content.Context
import android.net.Uri
import com.sovworks.eds.container.EdsContainer
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManagerBase
import com.sovworks.eds.settings.Settings

class LUKSLocation : LUKSLocationBase {
    constructor(
        uri: Uri?,
        lm: LocationsManagerBase?,
        context: Context?,
        settings: Settings?
    ) : super(uri, lm, context, settings)

    constructor(
        containerLocation: Location?,
        cont: EdsContainer?,
        context: Context?,
        settings: Settings?
    ) : super(containerLocation, cont, context, settings)

    private constructor(sibling: LUKSLocation) : super(sibling)

    override fun copy(): LUKSLocation? {
        return LUKSLocation(this)
    }
}
