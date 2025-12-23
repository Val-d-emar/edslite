package com.sovworks.eds.container

import com.sovworks.eds.fs.Path
import com.sovworks.eds.truecrypt.FormatInfo
import java.util.Collections

class EdsContainer @JvmOverloads constructor(
    path: Path,
    containerFormat: ContainerFormatInfo? = null,
    layout: VolumeLayout? = null
) :
    EdsContainerBase(path, containerFormat, layout) {
    override val formats: List<ContainerFormatInfo>
        get() = supportedFormats

    companion object {
        val supportedFormats: List<ContainerFormatInfo>
            get() {
                val al =
                    ArrayList<ContainerFormatInfo>()
                Collections.addAll(
                    al,
                    *SUPPORTED_FORMATS
                )
                return al
            }

        private val SUPPORTED_FORMATS = arrayOf(
            FormatInfo(),
            com.sovworks.eds.veracrypt.FormatInfo(),
            com.sovworks.eds.luks.FormatInfo()
        )

        fun findFormatByName(name: String?): ContainerFormatInfo {
            return Companion.findFormatByName(supportedFormats, name)
        }
    }
}
