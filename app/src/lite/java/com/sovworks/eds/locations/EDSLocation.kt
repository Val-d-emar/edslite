package com.sovworks.eds.locations

import com.sovworks.eds.fs.util.ContainerFSWrapper
import java.io.IOException

interface EDSLocation : OMLocation {
    interface ExternalSettings : com.sovworks.eds.locations.OMLocation.ExternalSettings {
        fun shouldOpenReadOnly(): Boolean

        fun setOpenReadOnly(`val`: Boolean)

        var autoCloseTimeout: Int
    }

    interface InternalSettings

    override val externalSettings: com.sovworks.eds.locations.OMLocation.ExternalSettings?

    val internalSettings: InternalSettings?

    @Throws(IOException::class)
    fun applyInternalSettings()

    @Throws(IOException::class)
    fun readInternalSettings()

    @Throws(IOException::class)
    fun writeInternalSettings()

    val lastActivityTime: Long

    val location: Location?

    @get:Throws(IOException::class)
    override val fS: ContainerFSWrapper?

    override fun copy(): EDSLocation?
}
