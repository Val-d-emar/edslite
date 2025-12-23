package com.sovworks.eds.fs.encfs

import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.fs.Directory.Contents
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.DirectoryWrapper
import com.sovworks.eds.fs.util.FilteredIterator
import java.io.IOException

class Directory(path: com.sovworks.eds.fs.encfs.Path?, realDir: com.sovworks.eds.fs.Directory?) :
    DirectoryWrapper(path, realDir) {
    @Throws(IOException::class)
    override fun getName(): String {
        return path.decodedPath.fileName
    }

    @Throws(IOException::class)
    override fun list(): Contents {
        val contents = base.list()
        return object : Contents {
            @Throws(IOException::class)
            override fun close() {
                contents.close()
            }

            override fun iterator(): MutableIterator<Path> {
                return FilteringIterator(
                    DirIterator(path.fileSystem, contents.iterator())
                )
            }
        }
    }

    override fun getPath(): com.sovworks.eds.fs.encfs.Path {
        return super.path as com.sovworks.eds.fs.encfs.Path
    }

    @Throws(IOException::class)
    override fun rename(newName: String) {
        if (path.namingCodecInfo.useChainedNamingIV() || path.fileSystem.config.useExternalFileIV()) throw UnsupportedOperationException()
        val newEncodedPath = path.parentPath!!.calcCombinedEncodedParts(newName)
        super.rename(newEncodedPath!!.fileName)
    }

    @Throws(IOException::class)
    override fun createFile(name: String): com.sovworks.eds.fs.File {
        var decodedPath = path.decodedPath
        if (decodedPath != null) decodedPath = decodedPath.combine(name)
        val newEncodedPath = path.calcCombinedEncodedParts(name)
        val res = super.createFile(newEncodedPath!!.fileName) as File
        res.path.encodedPath = newEncodedPath
        if (decodedPath != null) res.path.decodedPath = decodedPath
        res.outputStream.close()
        return res
    }

    @Throws(IOException::class)
    override fun createDirectory(name: String): com.sovworks.eds.fs.Directory {
        var decodedPath = path.decodedPath
        if (decodedPath != null) decodedPath = decodedPath.combine(name)
        val newEncodedPath = path.calcCombinedEncodedParts(name)
        val res = super.createDirectory(newEncodedPath!!.fileName) as Directory
        res.path.encodedPath = newEncodedPath
        if (decodedPath != null) res.path.decodedPath = decodedPath
        return res
    }

    @Throws(IOException::class)
    override fun moveTo(dst: com.sovworks.eds.fs.Directory) {
        if (path.namingCodecInfo.useChainedNamingIV() || path.fileSystem.config.useExternalFileIV()) throw UnsupportedOperationException()
        super.moveTo(dst)
    }

    @Throws(IOException::class)
    override fun getPathFromBasePath(basePath: Path): Path {
        return path.fileSystem.getPathFromRealPath(basePath)!!
    }

    private class DirIterator(private val _fs: FS?, srcIterator: Iterator<Path?>?) :
        IteratorConverter<Path?, com.sovworks.eds.fs.encfs.Path?>(
            srcIterator!!
        ) {
        override fun convert(src: Path): com.sovworks.eds.fs.encfs.Path? {
            try {
                return _fs!!.getPathFromRealPath(src)
            } catch (e: IOException) {
                log(e)
                return null
            }
        }
    }

    private class FilteringIterator(base: Iterator<com.sovworks.eds.fs.encfs.Path?>?) :
        FilteredIterator<Path?>(base) {
        override fun isValidItem(item: Path): Boolean {
            try {
                val p = item as com.sovworks.eds.fs.encfs.Path
                return !((p.parentPath!!.isRootDirectory && Config.Companion.CONFIG_FILENAME == p.encodedPath.fileName)
                        || p.decodedPath == null
                        )
            } catch (e: Throwable) {
                log(e)
                return false
            }
        }
    }
}
