package com.sovworks.eds.fs.std

import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.FSRecord
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.PathUtil
import java.io.IOException
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

abstract class StdFsRecord protected constructor(protected var _path: StdFsPath) : FSRecord {
    @Throws(IOException::class)
    override fun getLastModified(): Date {
        val cal: Calendar = GregorianCalendar()
        cal.timeInMillis = _path.javaFile.lastModified()
        return cal.time
    }

    @Throws(IOException::class)
    override fun setLastModified(dt: Date) {
        if (!_path.javaFile.setLastModified(dt.time)) throw IOException("Failed setting last modified date")
    }

    override fun getPath(): Path {
        return _path
    }

    @Throws(IOException::class)
    override fun delete() {
        if (_path.exists() && !_path.javaFile.delete()) throw IOException(
            String.format(
                "Failed deleting %s",
                _path.pathString
            )
        )
    }

    @Throws(IOException::class)
    override fun getName(): String {
        return _path.pathUtil.fileName
    }

    @Throws(IOException::class)
    override fun rename(newName: String) {
        moveTo(PathUtil.changeFileName(_path, newName) as StdFsPath)
    }

    @Throws(IOException::class)
    override fun moveTo(newParent: Directory) {
        moveTo(newParent.path.combine(name) as StdFsPath)
    }

    @Throws(IOException::class)
    fun moveTo(newPath: StdFsPath) {
        if (!_path.javaFile.renameTo(newPath.javaFile)) throw IOException(
            String.format(
                "Failed renaming %s to %s", _path.pathString, newPath.pathString
            )
        )
        _path = newPath
    }
}
