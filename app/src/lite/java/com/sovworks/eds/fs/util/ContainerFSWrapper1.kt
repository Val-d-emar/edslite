package com.sovworks.eds.fs.util

import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.android.filemanager.DirectorySettings
import com.sovworks.eds.android.filemanager.tasks.ReadDir
import com.sovworks.eds.android.filemanager.tasks.ReadDirBase.Companion.loadDirectorySettings
import com.sovworks.eds.fs.FileSystem
import java.io.IOException

class ContainerFSWrapper(baseFs: FileSystem) : ActivityTrackingFSWrapper(baseFs) {
    override fun setChangesListener(listener: ChangeListener?) {
        throw UnsupportedOperationException()
    }

    @Synchronized
    fun getDirectorySettings(path: com.sovworks.eds.fs.Path?): DirectorySettings? {
        if (path == null) return null
        if (_dirSettingsCache.containsKey(path)) return _dirSettingsCache[path]
        var ds: DirectorySettings? = null
        try {
            ds = ReadDir.loadDirectorySettings(path)
        } catch (e: IOException) {
            log(e)
        }
        _dirSettingsCache[path] = ds
        return ds
    }

    private val _dirSettingsCache = HashMap<com.sovworks.eds.fs.Path, DirectorySettings?>()
}
