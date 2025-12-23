package com.sovworks.eds.android.helpers

interface ContainerOpeningProgressReporter : ProgressReporter {
    fun setCurrentKDFName(name: String?)

    fun setCurrentEncryptionAlgName(name: String?)

    fun setContainerFormatName(name: String?)

    fun setIsHidden(`val`: Boolean)
}
