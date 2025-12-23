package com.sovworks.eds.fs.std

import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.util.PathBase
import com.sovworks.eds.fs.util.StringPathUtil
import java.io.IOException

class StdFsPath(private val _stdFS: StdFs, private val _pathString: String) : PathBase(
    _stdFS
) {
    @Throws(IOException::class)
    override fun exists(): Boolean {
        return javaFile.exists()
    }

    @Throws(IOException::class)
    override fun isFile(): Boolean {
        return javaFile.isFile
    }

    @Throws(IOException::class)
    override fun isDirectory(): Boolean {
        return javaFile.isDirectory
    }

    @Throws(IOException::class)
    override fun getDirectory(): Directory {
        return StdDirRecord(_stdFS, this)
    }

    @Throws(IOException::class)
    override fun getFile(): File {
        return StdFileRecord(this)
    }

    @get:Throws(IOException::class)
    val javaFile: java.io.File
        get() = java.io.File(_stdFS.rootDir.combine(_pathString).toString())

    override fun equals(o: Any?): Boolean {
        return if (o is StdFsPath)
            (absPath == o.absPath)
        else
            super.equals(o)
    }

    override fun getPathString(): String {
        return _pathString
    }

    private val absPath: StringPathUtil
        get() {
            try {
                val f = javaFile
                return StringPathUtil(if (f.exists()) f.canonicalPath else f.absolutePath)
            } catch (e: IOException) {
                return pathUtil
            }
        }
}
