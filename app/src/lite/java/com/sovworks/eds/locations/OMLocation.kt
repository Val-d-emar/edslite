package com.sovworks.eds.locations

import com.sovworks.eds.locations.Location.ExternalSettings

interface OMLocation : Openable {
    interface ExternalSettings : com.sovworks.eds.locations.Location.ExternalSettings {
        var password: ByteArray?

        var customKDFIterations: Int

        fun hasPassword(): Boolean
    }

    override val externalSettings: ExternalSettings?

    val isOpenOrMounted: Boolean
}
