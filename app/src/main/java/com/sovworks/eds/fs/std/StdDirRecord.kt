package com.sovworks.eds.fs.std

import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.Directory.Contents
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.errors.DirectoryIsNotEmptyException
import java.io.IOException

internal class StdDirRecord(private val _stdFs: StdFs, path: StdFsPath) : StdFsRecord(path),
    Directory {
    @Throws(IOException::class)
    override fun getTotalSpace(): Long {
        return _path.javaFile.totalSpace
    }

    @Throws(IOException::class)
    override fun getFreeSpace(): Long {
        // return _path.isRootDirectory() ? _path.getJavaFile().getFreeSpace() :
        // _stdFs.getRootPath().getDirectory().getFreeSpace();
        return _path.javaFile.freeSpace
    }

    @Throws(IOException::class)
    override fun delete() {
        if (_path.exists()) {
            val ff = _path.javaFile.listFiles()
            if (ff != null && ff.size > 0) throw DirectoryIsNotEmptyException("Directory is not empty: " + _path.pathDesc)
        }
        super.delete()
    }

    @Throws(IOException::class)
    override fun createDirectory(name: String): Directory {
        val newPath = _path.combine(name) as StdFsPath
        if (!newPath.javaFile.mkdir()) throw IOException("Failed creating folder")
        return StdDirRecord(_stdFs, newPath)
    }

    @Throws(IOException::class)
    override fun createFile(name: String): File {
        val newPath = _path.combine(name) as StdFsPath
        if (!newPath.javaFile.createNewFile()) throw IOException("Failed creating file")
        return StdFileRecord(newPath)
    }

    @Throws(IOException::class)
    override fun list(): Contents {
        val files = _path.javaFile.listFiles()
        val res =
            if (files == null) ArrayList() else ArrayList<Path>(files.size)
        if (files != null) for (f in files) res.add(_stdFs.getPath(f))

        return object : Contents {
            @Throws(IOException::class)
            override fun close() {
            }

            override fun iterator(): MutableIterator<Path> {
                return res.iterator()
            }
        }
    }

    init {
        require(!(path.exists() && !path.javaFile.isDirectory)) { "StdDirRecord error: file must be a directory" }
    }
}
