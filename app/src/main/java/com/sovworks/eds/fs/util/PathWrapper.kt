package com.sovworks.eds.fs.util

import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import java.io.IOException

abstract class PathWrapper(private val _fs: FileSystem, val base: Path) :
    Path {
    override fun getFileSystem(): FileSystem {
        return _fs
    }

    override fun getPathString(): String {
        return base.pathString
    }

    override fun getPathDesc(): String {
        return base.pathDesc
    }

    @Throws(IOException::class)
    override fun isRootDirectory(): Boolean {
        return base.isRootDirectory
    }

    @Throws(IOException::class)
    override fun exists(): Boolean {
        return base.exists()
    }

    @Throws(IOException::class)
    override fun isFile(): Boolean {
        return base.isFile
    }

    @Throws(IOException::class)
    override fun isDirectory(): Boolean {
        return base.isDirectory
    }

    @Throws(IOException::class)
    override fun combine(part: String): Path {
        return getPathFromBasePath(base.combine(part))
    }

    @Throws(IOException::class)
    override fun getDirectory(): Directory {
        return object : DirectoryWrapper(this, base.directory) {
            @Throws(IOException::class)
            override fun getPathFromBasePath(basePath: Path?): Path? {
                return this@PathWrapper.getPathFromBasePath(basePath)
            }
        }
    }

    @Throws(IOException::class)
    override fun getFile(): File {
        return object : FileWrapper(this, base.file) {
            @Throws(IOException::class)
            override fun getPathFromBasePath(basePath: Path?): Path? {
                return this@PathWrapper.getPathFromBasePath(basePath)
            }
        }
    }

    @Throws(IOException::class)
    override fun getParentPath(): Path {
        return getPathFromBasePath(base.parentPath)
    }

    override fun compareTo(another: Path): Int {
        return base.compareTo((another as PathWrapper).base)
    }

    override fun equals(o: Any?): Boolean {
        return o is PathWrapper && base == o.base
    }

    override fun hashCode(): Int {
        return base.hashCode()
    }

    override fun toString(): String {
        return base.toString()
    }

    @Throws(IOException::class)
    protected abstract fun getPathFromBasePath(basePath: Path?): Path
}
