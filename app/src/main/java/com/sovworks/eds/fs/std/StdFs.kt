package com.sovworks.eds.fs.std

import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.fs.util.Util
import java.io.File
import java.io.IOException

class StdFs protected constructor(rootDir: String?) : FileSystem {
    @Throws(IOException::class)
    override fun getPath(pathString: String): Path {
        return StdFsPath(this, pathString)
    }

    override fun getRootPath(): Path {
        try {
            return getPath("/")
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(IOException::class)
    override fun close(force: Boolean) {
    }

    override fun isClosed(): Boolean {
        return false
    }

    @Throws(IOException::class)
    fun getPath(f: File): Path {
        val pu = StringPathUtil(f.path)
        return getPath(pu.getSubPath(rootDir).toString())
    }

    val rootDir: StringPathUtil = StringPathUtil(rootDir)

    companion object {
        @Throws(IOException::class)
        fun makePath(vararg elements: Any?): StdFsPath {
            return Util.makePath(stdFs, *elements) as StdFsPath
        }

        @JvmStatic
        val stdFs: StdFs
            get() = getStdFs(null)

        @JvmStatic
        @Synchronized
        fun getStdFs(rootDir: String?): StdFs {
            if (rootDir == null || rootDir.length == 0 || rootDir == "/") {
                if (_rootStdFs == null) _rootStdFs = StdFs("")
                return _rootStdFs!!
            }

            return StdFs(rootDir)
        }

        private var _rootStdFs: StdFs? = null
    }
}
