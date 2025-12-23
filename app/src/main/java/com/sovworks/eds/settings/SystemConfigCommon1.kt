package com.sovworks.eds.settings

import java.io.File

abstract class SystemConfigCommon {
    abstract val cacheFolderPath: File?

    abstract val tmpFolderPath: File?

    abstract val privateExecFolderPath: File?

    abstract val fSMFolderPath: File?
}
