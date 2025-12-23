package com.sovworks.eds.fs.util

import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.Directory.Contents
import com.sovworks.eds.fs.FSRecord
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.Path
import java.io.IOException

abstract class DirectoryWrapper(path: Path, base: Directory) :
    FSRecordWrapper(path, base), Directory {
    @Throws(IOException::class)
    override fun getTotalSpace(): Long {
        return base.getTotalSpace()
    }

    @Throws(IOException::class)
    override fun getFreeSpace(): Long {
        return base.getFreeSpace()
    }

    @Throws(IOException::class)
    override fun createDirectory(name: String): Directory {
        val basePath: Path = base.createDirectory(name).getPath()
        return getPathFromBasePath(basePath)!!.directory
    }

    @Throws(IOException::class)
    override fun createFile(name: String): File {
        val basePath: Path = base.createFile(name).getPath()
        return getPathFromBasePath(basePath)!!.file
    }

    @Throws(IOException::class)
    override fun list(): Contents {
        return ContentsWrapper(base.list())
    }

    override val base: FSRecord?
        get() = super.getBase() as Directory

    protected inner class ContentsWrapper(private val _base: Contents) : Contents {
        @Throws(IOException::class)
        override fun close() {
            _base.close()
        }

        override fun iterator(): MutableIterator<Path> {
            return object : IteratorConverter<Path?, Path?>(_base.iterator()) {
                override fun convert(src: Path): Path {
                    try {
                        return getPathFromBasePath(src)!!
                    } catch (e: IOException) {
                        log(e)
                        return null
                    }
                }
            }
        }
    }
}
