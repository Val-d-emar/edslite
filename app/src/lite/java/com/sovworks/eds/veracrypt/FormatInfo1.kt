package com.sovworks.eds.veracrypt

import com.sovworks.eds.truecrypt.FormatInfo

class FormatInfo : FormatInfo() {
    override val volumeLayout: com.sovworks.eds.container.VolumeLayout
        get() = VolumeLayout()

    override val openingPriority: Int
        get() = 3

    override fun hasCustomKDFIterationsSupport(): Boolean {
        return true
    }

    companion object {
        val formatName: String = "VeraCrypt"
            get() = Companion.field
    }
}
