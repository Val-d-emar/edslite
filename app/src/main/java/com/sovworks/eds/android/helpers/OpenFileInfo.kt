package com.sovworks.eds.android.helpers

import com.sovworks.eds.locations.Location

class OpenFileInfo {
    var srcFileLocation: Location? = null
    var srcFolderLocation: Location? = null
    var devicePath: Location? = null
    var lastModified: Long = 0
    var srcLastModified: Long = 0
    var prevSize: Long = 0
    var isReadOnly: Boolean = false
}