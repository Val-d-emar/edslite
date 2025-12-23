package com.sovworks.eds.android.filemanager.records

import android.content.Context
import com.sovworks.eds.fs.Path
import com.sovworks.eds.locations.Location
import java.io.IOException

class LocRootDirRecord(context: Context?) : FolderRecord(context) {
    @Throws(IOException::class)
    override fun init(location: Location?, path: Path?) {
        super.init(location, path)
        _rootFolderName = super.getName()
        if ((_rootFolderName == null || _rootFolderName!!.isEmpty()) && location != null) _rootFolderName =
            location.title + "/"
    }

    override fun getName(): String {
        return _rootFolderName!!
    }

    override fun isFile(): Boolean {
        return false
    }

    override fun isDirectory(): Boolean {
        return true
    }

    private var _rootFolderName: String? = null
}