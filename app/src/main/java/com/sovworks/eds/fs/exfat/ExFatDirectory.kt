package com.sovworks.eds.fs.exfat

import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.Directory.Contents
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.Path
import java.io.IOException

internal class ExFatDirectory(exFat: ExFat, path: ExFatPath) : ExFatRecord(exFat, path), Directory {
    @Throws(IOException::class)
    override fun createDirectory(name: String): Directory {
        val newPath = path.pathUtil.combine(name).toString()
        synchronized(_exFat._sync) {
            val res = _exFat.makeDir(newPath)
            if (res != 0) throw IOException("Failed making directory. Error code = $res")
        }
        return ExFatDirectory(_exFat, ExFatPath(_exFat, newPath))
    }

    @Throws(IOException::class)
    override fun createFile(name: String): File {
        val newPath = path.pathUtil.combine(name).toString()
        synchronized(_exFat._sync) {
            val res = _exFat.makeFile(newPath)
            if (res != 0) throw IOException("Failed making directory. Error code = $res")
        }
        return ExFatFile(_exFat, ExFatPath(_exFat, newPath))
    }

    @Throws(IOException::class)
    override fun list(): Contents {
        val names = ArrayList<String>()
        synchronized(_exFat._sync) {
            val res = _exFat.readDir(_path.pathString, names)
            if (res != 0) throw IOException("readDir failed. Error code = $res")
        }
        val paths = ArrayList<Path>()
        val curPath = _path.pathUtil
        for (name in names) paths.add(ExFatPath(_exFat, curPath.combine(name).toString()))
        return object : Contents {
            @Throws(IOException::class)
            override fun close() {
            }

            override fun iterator(): MutableIterator<Path> {
                return paths.iterator()
            }
        }
    }

    @Throws(IOException::class)
    override fun delete() {
        synchronized(_exFat._sync) {
            val res = _exFat.rmdir(_path.pathString)
            if (res != 0) throw IOException("Delete failed. Error code = $res")
        }
    }

    @Throws(IOException::class)
    override fun getTotalSpace(): Long {
        synchronized(_exFat._sync) {
            val res = _exFat.totalSpace
            if (res < 0) throw IOException("Failed getting total space")
            return res
        }
    }

    @Throws(IOException::class)
    override fun getFreeSpace(): Long {
        synchronized(_exFat._sync) {
            val res = _exFat.freeSpace
            if (res < 0) throw IOException("Failed getting free space")
            return res
        }
    }
}
