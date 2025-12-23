package com.sovworks.eds.fs.util

import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.FSRecord
import com.sovworks.eds.fs.Path
import java.io.IOException
import java.util.Date

abstract class FSRecordWrapper(private var _path: Path, private val _base: FSRecord) : FSRecord {
    override fun getPath(): Path {
        return _path
    }

    @Throws(IOException::class)
    override fun getName(): String {
        return _base.name
    }

    @Throws(IOException::class)
    override fun rename(newName: String) {
        _base.rename(newName)
        path = getPathFromBasePath(_base.path)!!
    }

    @Throws(IOException::class)
    override fun getLastModified(): Date {
        return _base.lastModified
    }

    @Throws(IOException::class)
    override fun setLastModified(dt: Date) {
        _base.lastModified = dt
    }

    @Throws(IOException::class)
    override fun delete() {
        _base.delete()
    }

    @Throws(IOException::class)
    override fun moveTo(newParent: Directory) {
        _base.moveTo((newParent as DirectoryWrapper).base)
        path = getPathFromBasePath(_base.path)!!
    }

    open val base: FSRecord?
        get() = _base

    @Throws(IOException::class)
    protected abstract fun getPathFromBasePath(basePath: Path?): Path?

    protected fun setPath(path: Path) {
        _path = path
    }
}
