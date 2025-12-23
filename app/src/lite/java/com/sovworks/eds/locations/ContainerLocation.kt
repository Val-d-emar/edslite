package com.sovworks.eds.locations

import com.sovworks.eds.container.ContainerFormatInfo
import com.sovworks.eds.container.EdsContainer
import java.io.IOException

interface ContainerLocation : EDSLocation {
    interface ExternalSettings : com.sovworks.eds.locations.EDSLocation.ExternalSettings {
        var containerFormatName: String?

        var encEngineName: String?

        var hashFuncName: String?
    }

    override val externalSettings: OMLocation.ExternalSettings?

    @get:Throws(IOException::class)
    val edsContainer: EdsContainer?

    val supportedFormats: List<ContainerFormatInfo?>?
}
