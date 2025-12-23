package com.sovworks.eds.fs.util

import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import java.io.IOException

abstract class PathBase protected constructor(private val _fs: FileSystem) : Path {
    override fun getFileSystem(): FileSystem {
        return _fs
    }

    override fun getPathDesc(): String {
        return pathString
    }

    @Throws(IOException::class)
    override fun isRootDirectory(): Boolean {
        return isDirectory && parentPath == null
    }

    @Throws(IOException::class)
    override fun combine(part: String): PathBase {
        return _fs.getPath(pathUtil.combine(part).toString()) as PathBase
    }

    override fun equals(o: Any?): Boolean {
        val pu = pathUtil
        if (o is PathBase) {
            val opu = o.pathUtil
            return  /*((Path)o).getFileSystem().equals(getFileSystem()) && */((pu == null && opu == null)
                    || (pu != null && pu == opu))
        }

        if (o is String || o is StringPathUtil) return pu == o

        return super.equals(o)
    }

    override fun hashCode(): Int {
        val pu = pathUtil
        return pu?.hashCode() ?: 0
    }

    @Throws(IOException::class)
    override fun getParentPath(): Path? {
        val pu = pathUtil
        return if (pu == null || pu.isEmpty) null else _fs.getPath(pu.parentPath.toString())
    }

    override fun toString(): String {
        return pathString
    }

    override fun compareTo(other: Path): Int {
        return pathUtil.compareTo(StringPathUtil(other.pathString))
    }

    open val pathUtil: StringPathUtil
        get() = StringPathUtil(pathString)
}
