package com.sovworks.eds.fs.exfat

import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.FSRecord
import java.io.IOException
import java.util.Date

internal abstract class ExFatRecord(val _exFat: ExFat, protected var _path: ExFatPath) : FSRecord {
    override fun getPath(): ExFatPath {
        return _path
    }

    @Throws(IOException::class)
    override fun getName(): String {
        return _path.pathUtil.fileName
    }

    @Throws(IOException::class)
    override fun rename(newName: String) {
        val oldPath = path.pathUtil
        val newPath = oldPath.parentPath.combine(newName)
        val res = _exFat.rename(oldPath.toString(), newPath.toString())
        if (res != 0) throw IOException("Rename failed. Error code = $res")
        _path = ExFatPath(_exFat, newPath.toString())
    }

    @Throws(IOException::class)
    override fun getLastModified(): Date {
        return Date(_path.attr.modTime * 1000)
    }

    @Throws(IOException::class)
    override fun setLastModified(dt: Date) {
        _exFat.updateTime(_path.pathString, dt.time)
    }

    @Throws(IOException::class)
    override fun moveTo(newParent: Directory) {
        val oldPath = path.pathUtil
        val newPath =
            (newParent as ExFatDirectory).path.pathUtil.combine(oldPath.fileName)
        val res = _exFat.rename(oldPath.toString(), newPath.toString())
        if (res != 0) throw IOException("moveTo failed. Error code = $res")
        _path = ExFatPath(_exFat, newPath.toString())
    }
}
