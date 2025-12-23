package com.sovworks.eds.android.locations

import android.content.Context
import android.net.Uri
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.container.ContainerFormatInfo
import com.sovworks.eds.container.EdsContainer
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManagerBase
import com.sovworks.eds.settings.Settings
import com.sovworks.eds.truecrypt.FormatInfo

class TrueCryptLocation : ContainerBasedLocation {
    constructor(
        uri: Uri?,
        lm: LocationsManagerBase?,
        context: Context?,
        settings: Settings?
    ) : super(uri, lm, context, settings)

    constructor(location: Location?, context: Context?) : this(
        location,
        null,
        context,
        UserSettings.getSettings(context)
    )

    constructor(
        containerLocation: Location?,
        cont: EdsContainer?,
        context: Context?,
        settings: Settings?
    ) : super(containerLocation, cont, context, settings)

    constructor(sibling: TrueCryptLocation?) : super(sibling)


    override fun getLocationUri(): Uri {
        return makeUri(URI_SCHEME).build()
    }

    override fun copy(): TrueCryptLocation {
        return TrueCryptLocation(this)
    }

    override fun getSupportedFormats(): List<ContainerFormatInfo> {
        return listOf(containerFormatInfo)
    }

    public override fun getContainerFormatInfo(): ContainerFormatInfo {
        return FormatInfo()
    }

    companion object {
        const val URI_SCHEME: String = "tc"
    }
}
