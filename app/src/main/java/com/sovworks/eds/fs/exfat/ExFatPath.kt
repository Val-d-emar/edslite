package com.sovworks.eds.fs.exfat

import com.sovworks.eds.exceptions.NativeError
import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.FileStat
import com.sovworks.eds.fs.util.PathBase
import java.io.IOException

internal class ExFatPath(fs: ExFat?, private val _pathString: String) : PathBase(fs), Path {
    override fun getPathString(): String {
        return _pathString
    }

    @Throws(IOException::class)
    override fun exists(): Boolean {
        return attr != null
    }

    @Throws(IOException::class)
    override fun isFile(): Boolean {
        val attr = attr
        return attr != null && !attr.isDir
    }

    @Throws(IOException::class)
    override fun isDirectory(): Boolean {
        val attr = attr
        return attr != null && attr.isDir
    }

    @Throws(IOException::class)
    override fun getDirectory(): Directory {
        return ExFatDirectory(fileSystem, this)
    }

    @Throws(IOException::class)
    override fun getFile(): File {
        return ExFatFile(fileSystem, this)
    }

    override fun getFileSystem(): ExFat {
        return super.getFileSystem() as ExFat
    }

    @get:Throws(IOException::class)
    val attr: FileStat?
        get() {
            val ef = fileSystem
            synchronized(ef._sync) {
                val stat = FileStat()
                val res = ef.getAttr(stat, _pathString)
                if (res == NativeError.ENOENT) return null
                if (res != 0) throw IOException("getAttr failed. Error code = $res")
                return stat
            }
        }
}
