package com.sovworks.eds.container

import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.FileSystemInfo
import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException

interface ContainerFormatInfo {
    val formatName: String?
    @JvmField
    val volumeLayout: VolumeLayout?
    fun hasHiddenContainerSupport(): Boolean
    fun hasKeyfilesSupport(): Boolean
    fun hasCustomKDFIterationsSupport(): Boolean
    val maxPasswordLength: Int
    val hiddenVolumeLayout: VolumeLayout?

    @Throws(IOException::class, ApplicationException::class)
    fun formatContainer(io: RandomAccessIO?, layout: VolumeLayout?, fsInfo: FileSystemInfo?)
    val openingPriority: Int
}
