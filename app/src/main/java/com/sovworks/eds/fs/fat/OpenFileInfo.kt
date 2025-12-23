package com.sovworks.eds.fs.fat

import com.sovworks.eds.fs.File.AccessMode

class OpenFileInfo(var accessMode: AccessMode, var opTag: Any) {
    var refCount: Int = 1
}
