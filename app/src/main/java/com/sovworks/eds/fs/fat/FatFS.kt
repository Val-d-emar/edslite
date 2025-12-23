package com.sovworks.eds.fs.fat

import android.os.ParcelFileDescriptor
import android.util.Log
import com.sovworks.eds.android.BuildConfig
import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.Directory.Contents
import com.sovworks.eds.fs.FSRecord
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.File.AccessMode.Read
import com.sovworks.eds.fs.File.AccessMode.ReadWrite
import com.sovworks.eds.fs.File.AccessMode.ReadWriteTruncate
import com.sovworks.eds.fs.File.AccessMode.Write
import com.sovworks.eds.fs.File.AccessMode.WriteAppend
import com.sovworks.eds.fs.File.ProgressInfo
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.errors.DirectoryIsNotEmptyException
import com.sovworks.eds.fs.errors.FileInUseException
import com.sovworks.eds.fs.errors.FileSystemClosedException
import com.sovworks.eds.fs.errors.NoFreeSpaceLeftException
import com.sovworks.eds.fs.errors.WrongImageFormatException
import com.sovworks.eds.fs.util.PathBase
import com.sovworks.eds.fs.util.RandomAccessInputStream
import com.sovworks.eds.fs.util.RandomAccessOutputStream
import com.sovworks.eds.fs.util.Util
import com.sovworks.eds.settings.GlobalConfig
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Arrays
import java.util.Date
import kotlin.Int.Companion
import kotlin.math.min

// import android.util.Log;
open class FatFS protected constructor(var containerFile: RandomAccessIO) : FileSystem {
    private var _rootPath: Path? = null

    @Synchronized
    override fun getRootPath(): Path {
        if (_rootPath == null) try {
            _rootPath = getPath("")
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        return _rootPath
    }

    @Throws(IOException::class)
    fun init() {
        synchronized(_ioSyncer) {
            _bpb!!.read(containerFile)
            _emptyCluster = ByteArray(_bpb!!.bytesPerSector * _bpb!!.sectorsPerCluster)
            loadClusterTable()
        }
    }

    @Throws(IOException::class)
    override fun getPath(pathString: String): Path {
        val fp = FatPath(pathString)
        val components = fp.pathUtil.components
        for (cmp in components) if (!isValidFileName(cmp)) throw IOException(
            "Invalid path: $pathString"
        )
        return fp
    }

    @Throws(IOException::class)
    override fun close(force: Boolean) {
        _isClosing = true
        var timeLeft = PATH_LOCK_TIMEOUT
        while (timeLeft > 0) {
            synchronized(_openedFiles) {
                if (!_openedFiles.isEmpty()) {
                    val curTime = System.currentTimeMillis()
                    try {
                        (_openedFiles as Object).wait(timeLeft.toLong())
                    } catch (e: InterruptedException) {
                        break
                    }
                    timeLeft -= (System.currentTimeMillis() - curTime).toInt()
                } else break
            }
        }
        synchronized(_ioSyncer) {
            if (!force) {
                synchronized(_openedFiles) {
                    if (!_openedFiles.isEmpty()) {
                        if (GlobalConfig.isDebug()) {
                            val sb = StringBuilder()
                            for (p in _openedFiles.keys) sb.append(p.pathDesc).append(", ")
                            sb.delete(sb.length - 2, sb.length)
                            throw IOException("File system is in use. Opened files list: $sb")
                        } else throw IOException("File system is in use.")
                    }
                }
            }
        }
    }

    override fun isClosed(): Boolean {
        return _isClosing
    }

    fun isValidFileName(fileName: String): Boolean {
        return isValidFileNameImpl(fileName)
    }

    fun setReadOnlyMode(`val`: Boolean) {
        _readOnlyMode = `val`
    }

    fun getClusterOffset(clusterIndex: Int): Long {
        return _bpb!!.getClusterOffset(clusterIndex)
    }

    val sectorsPerCluster: Int
        get() = _bpb!!.sectorsPerCluster

    val bytesPerSector: Int
        get() = _bpb!!.bytesPerSector

    @Throws(FileInUseException::class)
    fun lockPath(path: Path, mode: AccessMode): Any {
        val tag = Any()
        lockPath(path, mode, tag)
        return tag
    }

    @Throws(FileInUseException::class)
    fun lockPath(path: Path, mode: AccessMode, opTag: Any) {
        var timeLeft = PATH_LOCK_TIMEOUT
        while (timeLeft > 0) {
            synchronized(_openedFiles) {
                if (_isClosing) throw FileInUseException("File system is closing")
                var ofi = _openedFiles[path]
                if (ofi != null) {
                    if (ofi.opTag !== opTag
                        && (mode == ReadWrite || mode == Write || ofi.accessMode == ReadWrite || ofi.accessMode == Write)
                    ) {
                        Log.i(
                            "EDS",
                            String.format("%s is busy waiting %d", path.pathString, timeLeft)
                        )
                        val curTime = System.currentTimeMillis()
                        try {
                            (_openedFiles as Object).wait(timeLeft.toLong())
                        } catch (e: InterruptedException) {
                            break
                        }
                        timeLeft -= (System.currentTimeMillis() - curTime).toInt()
                    } else {
                        ofi.refCount++
                        if (ofi.accessMode != mode && ofi.accessMode == Read) ofi.accessMode = mode
                        return
                    }
                } else {
                    ofi = OpenFileInfo(mode, opTag)
                    _openedFiles[path] = ofi
                    return
                }
            }
        }

        throw FileInUseException("File is in use " + path.pathString)
    }

    fun releasePathLock(path: Path) {
        synchronized(_openedFiles) {
            val ofi = _openedFiles[path]
            if (ofi != null) {
                ofi.refCount--
                check(ofi.refCount >= 0) { "$path ref count < 0" }
                if (ofi.refCount == 0) {
                    _openedFiles.remove(path)
                    (_openedFiles as Object).notifyAll()
                }
            }
        }
    }

    @Throws(IOException::class)
    fun getDirWriter(targetPath: FatPath, opTag: Any): DirWriter? {
        lockPath(targetPath, ReadWrite, opTag)
        try {
            return getDirWriterNoLock(targetPath, opTag)
        } catch (e: IOException) {
            releasePathLock(targetPath)
            throw e
        }
    }

    @Throws(IOException::class)
    fun getDirWriterNoLock(targetPath: FatPath, opTag: Any): DirWriter? {
        if (targetPath.pathUtil.isEmpty) return rootDirOutputStream
        val de = getCachedDirEntry(targetPath, opTag)
        if (de == null || de.isFile) throw FileNotFoundException()
        return DirOutputStream(
            ClusterChainIO(de.startCluster, targetPath, -1, Write)
        )
    }

    @Throws(IOException::class)
    fun getDirReader(targetPath: FatPath, opTag: Any): DirReader? {
        lockPath(targetPath, Read, opTag)
        try {
            return getDirReaderNoLock(targetPath, opTag)
        } catch (e: IOException) {
            releasePathLock(targetPath)
            throw e
        }
    }

    @Throws(IOException::class)
    fun getDirReaderNoLock(targetPath: FatPath, opTag: Any): DirReader? {
        if (targetPath.pathUtil.isEmpty) return rootDirInputStream
        val de = getCachedDirEntry(targetPath, opTag)
        if (de == null || de.isFile) throw FileNotFoundException("Path not found: $targetPath")
        return DirInputStream(ClusterChainIO(de.startCluster, targetPath, -1, Read))
    }

    abstract inner class FatRecord protected constructor(protected var _path: FatPath) : FSRecord {
        override fun getPath(): Path {
            return _path
        }

        override fun getName(): String {
            return _path.pathUtil.fileName
        }

        @get:Throws(IOException::class)
        @get:Suppress("unused")
        val createDate: Date?
            get() {
                val entry = _path.entry
                return if (entry != null) entry.createDateTime else Date()
            }

        @Throws(IOException::class)
        override fun getLastModified(): Date {
            val entry = _path.entry
            return if (entry != null) entry.lastModifiedDateTime else Date()
        }

        @Throws(IOException::class)
        override fun setLastModified(dt: Date) {
            if (_readOnlyMode) throw IOException(
                String.format(
                    "Can't update file %s: file system is opened in read only mode",
                    _path.pathString
                )
            )

            val parentPath = _path.parentPath as FatPath
                ?: throw IOException("Can't update last modified time of the root directory")

            val tag = lockPath(_path, Write)
            try {
                val entry = _path.getEntry(tag)
                    ?: throw IOException("setLastModified error: failed opening source path: $_path")
                entry.lastModifiedDateTime = dt
                entry.writeEntry(this@FatFS, parentPath, tag)
            } finally {
                releasePathLock(_path)
            }
        }

        @get:Throws(IOException::class)
        @get:Suppress("unused")
        val accessDate: Date?
            get() {
                val entry = _path.entry
                return if (entry != null) entry.lastAccessDate else Date()
            }

        @Throws(IOException::class)
        override fun rename(newName: String) {
            if (_readOnlyMode) throw IOException(
                String.format(
                    "Can't rename file %s: file system is opened in read only mode",
                    _path.pathString
                )
            )

            val parentPath = _path.parentPath as FatPath
                ?: throw IOException("Can't rename root directory")

            val tag = lockPath(parentPath, Write)
            try {
                val entry = _path.getEntry(tag)
                    ?: throw IOException("rename error: failed opening source path: $_path")
                val newPath = parentPath.combine(newName) as FatPath
                val destEntry = newPath.getEntry(tag)
                if (destEntry != null && destEntry !== entry) {
                    if (entry.isDir) throw IOException("rename error: destination path already exists: $newPath")
                    else deleteEntry(destEntry, parentPath, tag)
                }
                if (entry.offset >= 0) {
                    entry.deleteEntry(this@FatFS, parentPath, tag)
                    entry.offset = -1
                    entry.dosName = null
                }
                cacheDirEntry(_path, null)
                entry.name = newName
                entry.writeEntry(this@FatFS, parentPath, tag)
                _path = newPath
                cacheDirEntry(_path, entry)
            } finally {
                releasePathLock(parentPath)
            }
        }

        @Throws(IOException::class)
        override fun moveTo(newParent: Directory) {
            if (_readOnlyMode) throw IOException(
                String.format(
                    "Can't rename file %s: file system is opened in read only mode",
                    _path.pathString
                )
            )

            val parentPath = _path.parentPath as FatPath
                ?: throw IOException("Can't rename root directory")

            val newParentPath = newParent.path as FatPath
            if (newParentPath == parentPath) return

            val tag = lockPath(parentPath, Write)
            try {
                lockPath(newParentPath, Write, tag)
                try {
                    val entry = _path.getEntry(tag)
                        ?: throw IOException("rename error: failed opening source path: $_path")
                    val newPath = newParentPath.combine(name) as FatPath
                    if (entry.isDir && _path.pathUtil.isParentDir(newParentPath.pathUtil)) throw IOException(
                        "rename error: can't move directory to it's subdirectory: $_path"
                    )
                    val destEntry = newPath.getEntry(tag)
                    if (destEntry != null && destEntry !== entry) {
                        if (entry.isDir) throw IOException("rename error: destination path already exists: $newPath")
                        else deleteEntry(destEntry, parentPath, tag)
                    }
                    if (entry.offset >= 0) {
                        entry.deleteEntry(this@FatFS, parentPath, tag)
                        entry.offset = -1
                    }
                    cacheDirEntry(_path, null)
                    entry.writeEntry(this@FatFS, newParentPath, tag)
                    _path = newPath
                    cacheDirEntry(_path, entry)
                } finally {
                    releasePathLock(newParentPath)
                }
            } finally {
                releasePathLock(parentPath)
            }
        }
    }

    internal inner class DirIterator : MutableIterator<Path> {
        /**
         * Returns <tt>true</tt> if the iteration has more elements. (In other words, returns
         * <tt>true</tt> if <tt>next</tt> would return an element rather than throwing an exception.)
         *
         * @return <tt>true</tt> if the iterator has more elements.
         */
        override fun hasNext(): Boolean {
            return _next != null
        }

        /**
         * Returns the next element in the iteration.
         *
         * @return the next element in the iteration.
         * @throws java.util.NoSuchElementException iteration has no more elements.
         */
        override fun next(): Path {
            if (_next == null) throw NoSuchElementException()

            val res: FatPath
            try {
                res = _path!!.combine(_next!!.name) as FatPath
            } catch (e: IOException) {
                throw RuntimeException(e)
            }

            val li = logAquiring("dc")
            try {
                synchronized(_dirEntriesCache) {
                    logAquired(li)
                    if (!_dirEntriesCache.containsKey(res)) cacheDirEntry(res, _next)
                }
            } finally {
                logReleased(li)
            }

            setNext()
            return res
        }

        /**
         * Removes from the underlying collection the last element returned by the iterator (optional
         * operation). This method can be called only once per call to <tt>next</tt>. The behavior of an
         * iterator is unspecified if the underlying collection is modified while the iteration is in
         * progress in any way other than by calling this method.
         *
         * @throws UnsupportedOperationException if the <tt>remove</tt> operation is not supported by
         * this Iterator.
         * @throws IllegalStateException if the <tt>next</tt> method has not yet been called, or the
         * <tt>remove</tt> method has already been called after the last call to the <tt>next</tt>
         * method.
         */
        override fun remove() {
            throw UnsupportedOperationException()
        }

        fun reset(path: FatPath?, dirStream: DirReader?) {
            _dirStream = dirStream
            setNext()
            _path = path
        }

        @Throws(IOException::class, NoSuchElementException::class)
        fun nextDirEntry(): DirEntry {
            if (_next == null) throw NoSuchElementException()
            val res: DirEntry = _next
            _next = DirEntry.Companion.readEntry(_dirStream!!)
            return res
        }

        private var _dirStream: DirReader? = null
        private var _next: DirEntry? = null
        private var _path: FatPath? = null

        private fun setNext() {
            try {
                do {
                    _next = DirEntry.Companion.readEntry(_dirStream!!)
                } while (_next != null && (_next!!.name == "." || _next!!.name == ".."))
            } catch (e: IOException) {
                log(e)
                _next = null
            }
        }
    }

    internal inner class FatDirectory(path: FatPath) : FatRecord(path), Directory {
        @Throws(IOException::class)
        override fun delete() {
            if (_readOnlyMode) throw IOException(
                String.format(
                    "Can't delete directory %s: file system is opened in read only mode",
                    _path.pathString
                )
            )
            if (_path.isRootDirectory) throw IOException("Can't delete root directory")

            val tag = lockPath(_path, Write)
            try {
                val entry = _path.getEntry(tag) ?: return
                if (!entry.isDir) throw IOException("Specified path is not a directory: " + _path.pathString)

                val dc = list(tag)
                try {
                    for (rec in dc) if (!(rec as FatPath).pathUtil.isSpecial) throw DirectoryIsNotEmptyException(
                        _path.pathString, "Directory is not empty: " + _path.pathString
                    )
                } finally {
                    dc.close()
                }
                deleteEntry(entry, _path.parentPath as FatPath, tag)
                cacheDirEntry(_path, null)
            } finally {
                releasePathLock(_path)
            }
        }

        @Throws(IOException::class)
        override fun createDirectory(name: String): Directory {
            if (_readOnlyMode) throw IOException("Can't create directory: file system is opened in read only mode")
            if (!isValidFileName(name)) throw IOException("Invalid file name: $name")

            val tag = lockPath(_path, Write)
            try {
                val newPath = _path.combine(name) as FatPath
                val entry = getCachedDirEntry(newPath, tag)
                // if (entry != null) throw new IOException("File record with the specified name already
                // exists: " + _path.getPathString());
                if (entry == null) makeNewDir(_path, name, tag)
                return newPath.directory
            } finally {
                releasePathLock(_path)
            }
        }

        @Throws(IOException::class)
        override fun createFile(name: String): File {
            if (_readOnlyMode) throw IOException("Can't create directory: file system is opened in read only mode")
            if (!isValidFileName(name)) throw IOException("Invalid file name: $name")

            val tag = lockPath(_path, Write)
            try {
                val newPath = _path.combine(name) as FatPath
                val entry = newPath.getEntry(tag)
                if (entry != null && entry.isDir) throw IOException("Can't create file: there is a directory with the same name.")
                else if (entry != null && entry.isFile) deleteEntry(entry, _path, tag)
                makeNewFile(_path, name, tag)
                return newPath.file
            } finally {
                releasePathLock(_path)
            }
        }

        @Throws(IOException::class)
        override fun list(): Contents {
            return list(Any())
        }

        @Throws(IOException::class)
        override fun getTotalSpace(): Long {
            return _bpb!!.getTotalSectorsNumber() * _bpb!!.bytesPerSector
        }

        @Throws(IOException::class)
        override fun getFreeSpace(): Long {
            // long totalSpace = getTotalSpace();
            var freeSpace: Long = 0
            val bytesPerCluster = _bpb!!.sectorsPerCluster * _bpb!!.bytesPerSector
            synchronized(_ioSyncer) {
                if (this.containerFile == null) throw FileSystemClosedException()
                for (i in 2..<_totalClusterNumber) {
                    val clusterIndex =
                        (if (this.clusterTable == null) readNextClusterIndex(i) else clusterTable!![i])
                    if (clusterIndex == 0)  // clusterIndex >=0 && clusterIndex!=LAST_CLUSTER)
                        freeSpace += bytesPerCluster.toLong()
                }
            }
            return freeSpace // totalSpace - usedSpace;
        }

        @Throws(IOException::class)
        fun list(opTag: Any): Contents {
            val stream = getDirReader(_path, opTag)
            return object : Contents {
                override fun iterator(): MutableIterator<Path> {
                    val it: DirIterator = DirIterator()
                    it.reset(_path, stream)
                    return it
                }

                @Throws(IOException::class)
                override fun close() {
                    stream!!.close()
                }
            }
        }
    }

    internal inner class FatFile(path: FatPath) : FatRecord(path), File {
        @Throws(IOException::class)
        override fun getSize(): Long {
            val entry = _path.entry
            if (entry == null || !entry.isFile) throw FileNotFoundException("File not found: " + _path.pathString)
            return entry.fileSize
        }

        override fun getFileDescriptor(accessMode: AccessMode): ParcelFileDescriptor? {
            return null
        }

        @Throws(IOException::class)
        override fun copyToOutputStream(
            output: OutputStream, offset: Long, count: Long, progressInfo: ProgressInfo
        ) {
            Util.copyFileToOutputStream(
                output,
                this, offset, count, progressInfo
            )
        }

        @Throws(IOException::class)
        override fun copyFromInputStream(
            input: InputStream, offset: Long, count: Long, progressInfo: ProgressInfo
        ) {
            Util.copyFileFromInputStream(
                input,
                this, offset, count, progressInfo
            )
        }

        @Throws(IOException::class)
        override fun delete() {
            if (_readOnlyMode) throw IOException(
                String.format(
                    "Can't delete file %s: file system is opened in read only mode",
                    _path.pathString
                )
            )
            val tag = lockPath(_path, Write)
            try {
                delete(tag)
            } finally {
                releasePathLock(_path)
            }
        }

        @Throws(IOException::class)
        override fun getInputStream(): RandomAccessInputStream {
            return RandomAccessInputStream(getRandomAccessIO(Read))
        }

        @Throws(IOException::class)
        override fun getOutputStream(): RandomAccessOutputStream {
            val tag = lockPath(_path, ReadWrite)
            try {
                return RandomAccessOutputStream(getRandomAccessIO(ReadWriteTruncate, tag))
            } catch (e: IOException) {
                releasePathLock(_path)
                throw e
            }
        }

        @Throws(IOException::class)
        override fun getRandomAccessIO(accessMode: AccessMode): FileIO {
            val tag = lockPath(_path, accessMode)
            try {
                return getRandomAccessIO(accessMode, tag)
            } catch (e: IOException) {
                releasePathLock(_path)
                throw e
            }
        }

        @Throws(IOException::class)
        private fun getRandomAccessIO(accessMode: AccessMode, tag: Any): FileIO {
            if (_readOnlyMode && accessMode != Read) throw IOException(
                String.format(
                    "Can't open file %s for writing: file system is opened in read only mode",
                    _path.pathString
                )
            )
            var entry = _path.getEntry(tag)
            if (entry == null) {
                if (accessMode != Read) {
                    val newFilePath =
                        _path.parentPath.directory.createFile(name).path as FatPath
                    entry = newFilePath.getEntry(tag)
                } else throw FileNotFoundException("File not found: $_path")
            } else if (entry.isDir) throw FileNotFoundException("File name conflicts with directory name: $_path")
            return FileIO(this@FatFS, entry!!, _path, accessMode, tag)
        }

        @Throws(IOException::class)
        fun delete(tag: Any) {
            if (_readOnlyMode) throw IOException(
                String.format(
                    "Can't delete file %s: file system is opened in read only mode",
                    _path.pathString
                )
            )
            val entry = _path.getEntry(tag) ?: return
            if (!entry.isFile) throw IOException(
                "deleteFile error: specified path is not a file: " + _path.pathString
            )
            deleteEntry(entry, _path.parentPath as FatPath, tag)
            cacheDirEntry(_path, null)
        }
    }

    inner class FatPath(private val _pathString: String) : PathBase(this@FatFS) {
        @Throws(IOException::class)
        override fun exists(): Boolean {
            return pathUtil.isEmpty || entry != null
        }

        @Throws(IOException::class)
        override fun isFile(): Boolean {
            val entry = entry
            return entry != null && entry.isFile
        }

        @Throws(IOException::class)
        override fun isDirectory(): Boolean {
            if (pathUtil.isEmpty) return true
            val entry = entry
            return entry != null && entry.isDir
        }

        @Throws(IOException::class)
        override fun getDirectory(): Directory {
            val entry = entry
            if (entry != null && !entry.isDir) throw IOException("$pathString is not a directory")
            return FatDirectory(this)
        }

        @Throws(IOException::class)
        override fun getFile(): File {
            val entry = entry
            if (entry != null && !entry.isFile) throw IOException("$pathString is not a file")
            return FatFile(this)
        }

        @get:Throws(IOException::class)
        val entry: DirEntry?
            get() = getCachedDirEntry(this, Any())

        @Throws(IOException::class)
        fun getEntry(opTag: Any): DirEntry? {
            return getCachedDirEntry(this, opTag)
        }

        override fun getPathString(): String {
            return if (_pathString.length == 0) "/" else _pathString
        }
    }

    protected var _readOnlyMode: Boolean = false
    protected var _isClosing: Boolean = false
    protected var _bpb: BPB? = null
    protected var _clusterIndexSize: Byte = 0
    protected var _totalClusterNumber: Int = 0
    var clusterTable: IntArray?
        protected set
    protected val _openedFiles: MutableMap<Path, OpenFileInfo> = HashMap()
    protected val _dirEntriesCache: MutableMap<Path, DirEntry?> = HashMap()
    protected val _ioSyncer: Any = Any()
    protected var _emptyCluster: ByteArray

    @Throws(IOException::class)
    protected open fun writeHeader() {
        containerFile.seek(0)
        val cnt =
            (_bpb!!.reservedSectors * _bpb!!.bytesPerSector + _bpb!!.rootDirEntries * 32 + _bpb!!.sectorsPerFat * _bpb!!.numberOfFATs * _bpb!!.bytesPerSector)
        val buf = ByteArray(512)
        var i = 0
        while (i < cnt) {
            containerFile.write(buf, 0, buf.size)
            i += 512
        }
        containerFile.seek(0)
        containerFile.write(FAT_START, 0, FAT_START.size)
        _bpb!!.write(containerFile)
    }

    @Throws(IOException::class)
    protected fun copySectors(startSector: Int, destSector: Int, count: Int) {
        val buf = ByteArray(_bpb!!.bytesPerSector)
        val startOffset = startSector * _bpb!!.bytesPerSector
        val destOffset = destSector * _bpb!!.bytesPerSector
        for (i in 0..<count) {
            containerFile.seek((startOffset + i * _bpb!!.bytesPerSector).toLong())
            Util.readBytes(containerFile, buf, buf.size)
            containerFile.seek((destOffset + i * _bpb!!.bytesPerSector).toLong())
            containerFile.write(buf, 0, buf.size)
        }
    }

    @Throws(IOException::class)
    protected open fun writeEmptyClusterTable() {
        containerFile.seek(getClusterIndexPosition(0).toLong())
        val bytesPerFat = _bpb.getSectorsPerFat() * _bpb!!.bytesPerSector
        for (i in 0..<bytesPerFat) containerFile.write(0)
        writeClusterIndex(0, _bpb!!.mediaType.toInt() or 0x0FFFFF00)
        writeClusterIndex(1, LAST_CLUSTER)
    }

    @Throws(IOException::class)
    protected fun writeFatBackup() {
        copySectors(
            _bpb!!.reservedSectors,
            _bpb!!.reservedSectors + _bpb.getSectorsPerFat(),
            _bpb.getSectorsPerFat()
        )
    }

    protected fun calcTotalClustersNumber(): Int {
        // return bpb.sectorsPerFat * bpb.bytesPerSector * 8 / clusterIndexSize;
        val root_dir_sectors: Int =
            _bpb!!.rootDirEntries * DirEntry.Companion.RECORD_SIZE / _bpb!!.bytesPerSector
        val data_sectors =
            (_bpb!!.getTotalSectorsNumber()
                    - (_bpb!!.reservedSectors
                    + _bpb!!.numberOfFATs * _bpb.getSectorsPerFat() + root_dir_sectors))
        return 2 + (data_sectors / _bpb!!.sectorsPerCluster).toInt()
    }

    @Suppress("unused")
    protected open fun getDataRegionSize(totalSize: Long): Long {
        val fatSize = _bpb.getSectorsPerFat() * _bpb!!.bytesPerSector
        return (totalSize
                - fatSize * _bpb!!.numberOfFATs - _bpb!!.reservedSectors * _bpb!!.bytesPerSector - _bpb!!.rootDirEntries * DirEntry.Companion.RECORD_SIZE)
    }

    protected open fun calcSectorsPerCluster(volumeSize: Long): Int {
        return getOptimalClusterSize(volumeSize, SECTOR_SIZE)
    }

    protected open val reservedSectorsNumber: Short
        get() = 2

    protected open fun getNumClusters(volumeSize: Long): Int {
        val bytesPerCluster = _bpb!!.sectorsPerCluster * _bpb!!.bytesPerSector
        return (volumeSize / bytesPerCluster).toInt()
    }

    protected open fun initBPB(size: Long) {
        val numSectors = size / SECTOR_SIZE

        _bpb!!.bytesPerSector = SECTOR_SIZE
        _bpb!!.sectorsPerCluster = calcSectorsPerCluster(size)
        _bpb!!.reservedSectors = reservedSectorsNumber.toInt()
        _bpb!!.numberOfFATs = 2
        _bpb!!.rootDirEntries = 512

        _bpb!!.mediaType = 0xF8

        // bpb.physicalDriveNumber = 0x80;
        _bpb!!.physicalDriveNumber = 0
        _bpb!!.extendedBootSignature = 0x29
        _bpb!!.volumeSerialNumber = 12345
        _bpb!!.volumeLabel = byteArrayOf(
            'E'.code.toByte(),
            'D'.code.toByte(),
            'S'.code.toByte(),
            ' '.code.toByte(),
            ' '.code.toByte(),
            ' '.code.toByte(),
            ' '.code.toByte(),
            ' '.code.toByte(),
            ' '.code.toByte(),
            ' '.code.toByte(),
            ' '.code.toByte()
        )

        //		fatsecs = ft->num_sectors - (ft->size_root_dir + ft->sector_size - 1) / ft->sector_size -
        // ft->reserved;
        //		ft->cluster_count = (int) (((__int64) fatsecs * ft->sector_size) / (ft->cluster_size *
        // ft->sector_size));
        //		ft->fat_length = (((ft->cluster_count * 3 + 1) >> 1) + ft->sector_size - 1) /
        // ft->sector_size;
        val rootDirSize = _bpb!!.rootDirEntries * 32

        val dataSectors =
            (numSectors
                    - (rootDirSize + _bpb!!.bytesPerSector - 1) / _bpb!!.bytesPerSector - _bpb!!.reservedSectors)
        val clusterCount =
            ((dataSectors * _bpb!!.bytesPerSector) / (_bpb!!.sectorsPerCluster * _bpb!!.bytesPerSector)).toInt()
        _bpb!!.sectorsPerFat =
            (((clusterCount * 3 + 1) shr 1) + _bpb!!.bytesPerSector - 1) / _bpb!!.bytesPerSector

        // clusterCount -= bpb.sectorsPerFat*bpb.numberOfFATs / bpb.sectorsPerCluster;
        if (numSectors > 65535) {
            _bpb!!.sectorsBig = numSectors
            _bpb!!.totalSectorsNumber = 0
        } else {
            _bpb!!.sectorsBig = 0
            _bpb!!.totalSectorsNumber = numSectors.toInt()
        }

        Arrays.fill(_bpb!!.fileSystemLabel, ' '.code.toByte())
        val label: ByteArray = BPB.Companion.FAT12_LABEL.toByteArray()
        System.arraycopy(label, 0, _bpb!!.fileSystemLabel, 0, label.size)
        _bpb!!.calcParams()
    }

    @Throws(IOException::class)
    protected fun loadClusterTable() {
        _totalClusterNumber = calcTotalClustersNumber()
        clusterTable = IntArray(_totalClusterNumber)
        for (i in 0..<_totalClusterNumber) clusterTable!![i] = readNextClusterIndex(i)
    }

    @Throws(IOException::class)
    protected fun freeClusters(startCluster: Int) {
        synchronized(_ioSyncer) {
            var ci = startCluster
            while (ci > 0 && ci != LAST_CLUSTER) {
                val tci = ci
                ci = getNextClusterIndex(ci)
                setNextClusterIndex(tci, 0, true)
            }
        }
    }

    @Throws(IOException::class)
    protected fun deleteEntry(entry: DirEntry, basePath: FatPath, opTag: Any) {
        lockPath(basePath, ReadWrite, opTag)
        try {
            freeClusters(entry.startCluster)
            entry.deleteEntry(this, basePath, opTag)
        } finally {
            releasePathLock(basePath)
        }
    }

    @Throws(IOException::class)
    protected fun makeNewEntry(setCluster: Boolean): DirEntry {
        val entry = DirEntry()
        if (setCluster) {
            synchronized(_ioSyncer) {
                entry.startCluster = attachFreeCluster(0, true)
                zeroCluster(entry.startCluster)
            }
        }
        return entry
    }

    @Throws(IOException::class)
    protected fun makeNewFile(parentPath: FatPath, name: String?, opTag: Any): DirEntry {
        val entry = makeNewEntry(false)
        entry.name = name
        entry.isDir = false
        entry.writeEntry(this, parentPath, opTag)
        updateModTime(parentPath, opTag)
        val newPath = parentPath.combine(name) as FatPath
        cacheDirEntry(newPath, entry)
        return entry
    }

    @Throws(IOException::class)
    protected fun makeNewDir(parentPath: FatPath, name: String?, opTag: Any): DirEntry {
        val entry = makeNewEntry(true)
        entry.name = name
        entry.isDir = true
        entry.writeEntry(this, parentPath, opTag)
        updateModTime(parentPath, opTag)
        val newPath = parentPath.combine(name) as FatPath
        cacheDirEntry(newPath, entry)

        val s = getDirWriter(newPath, opTag)
        try {
            var dotEntry = DirEntry(0)
            dotEntry.name = "."
            dotEntry.isDir = true
            dotEntry.startCluster = entry.startCluster
            dotEntry.writeEntry(FileName("."), s!!)

            dotEntry = DirEntry(DirEntry.Companion.RECORD_SIZE)
            dotEntry.name = ".."
            dotEntry.isDir = true
            if (!parentPath.isRootDirectory) {
                val parentEntry = getCachedDirEntry(parentPath, opTag)
                if (parentEntry != null) dotEntry.startCluster = parentEntry.startCluster
            }
            dotEntry.writeEntry(FileName(".."), s)
            s.write(0)
        } finally {
            s!!.close()
        }
        return entry
    }

    @Throws(IOException::class)
    private fun updateModTime(path: FatPath, tag: Any) {
        val parentPath = path.parentPath as FatPath
        if (parentPath != null) {
            val entry = getDirEntry(path, tag)
            if (entry != null) {
                entry.lastModifiedDateTime = Date()
                entry.writeEntry(this, parentPath, tag)
            }
        }
    }

    protected fun getClusterIndexPosition(clusterIndex: Int): Int {
        return _bpb!!.reservedSectors * _bpb!!.bytesPerSector + clusterIndex * _clusterIndexSize / 8
    }

    @Throws(IOException::class)
    protected open fun readNextClusterIndex(clusterIndex: Int): Int {
        containerFile.seek(getClusterIndexPosition(clusterIndex).toLong())
        return 0
    }

    @Throws(IOException::class)
    protected fun getNextClusterIndex(clusterIndex: Int): Int {
        return if (clusterTable == null) readNextClusterIndex(clusterIndex) else clusterTable!![clusterIndex]
    }

    @get:Throws(IOException::class)
    protected open val rootDirInputStream: DirReader?
        get() = null

    @get:Throws(IOException::class)
    protected open val rootDirOutputStream: DirWriter?
        get() = null

    protected fun cacheDirEntry(path: FatPath, entry: DirEntry?) {
        val li = logAquiring("dc")
        try {
            synchronized(_dirEntriesCache) {
                logAquired(li)
                if (_dirEntriesCache.size > MAX_DIR_ENTRIES_CACHE) _dirEntriesCache.clear()
                _dirEntriesCache.put(path, entry)
            }
        } finally {
            logReleased(li)
        }
    }

    @Throws(IOException::class)
    protected fun getCachedDirEntry(path: FatPath, opTag: Any): DirEntry? {
        val li = logAquiring("dc")
        try {
            synchronized(_dirEntriesCache) {
                logAquired(li)
                if (_dirEntriesCache.containsKey(path)) { /**/ DEBUG * /
                    // DirEntry de = _dirEntriesCache.get(path);
                    // Log.d("EDS", String.format("DirEntry %s found in cache for %s .
                    // ",de,path.getPathString()));
                    // return de;
                    return _dirEntriesCache[path]
                }
            }
        } finally {
            logReleased(li)
        }
        val res = getDirEntry(path, opTag)
        // Log.d("EDS", String.format("DirEntry %s not found in cache for %s and was created.
        // ",res,path.getPathString()));
        cacheDirEntry(path, res)
        return res
    }

    @Throws(IOException::class)
    fun getDirEntry(targetPath: FatPath, opTag: Any): DirEntry? {
        val pathComponents = targetPath.pathUtil.components
        if (pathComponents.size == 0) return null

        var res: DirEntry? = null
        val it: DirIterator = DirIterator()
        var p = rootPath as FatPath
        for (dir in pathComponents) {
            val dirStream: DirReader?
            lockPath(p, Read, opTag)
            try {
                dirStream = if (res == null) rootDirInputStream
                else {
                    if (res.isFile) return null
                    else if (res.name == ".." && res.startCluster == 0) rootDirInputStream
                    else DirInputStream(ClusterChainIO(res.startCluster, p, -1, Read))
                }
            } catch (e: IOException) {
                releasePathLock(p)
                throw e
            }
            try {
                it.reset(p, dirStream)
                res = null
                while (it.hasNext()) {
                    val entry = it.nextDirEntry()
                    if (dir.equals(entry.name, ignoreCase = true)) {
                        res = entry
                        break
                    }
                }
            } finally {
                dirStream!!.close()
            }

            if (res == null) return null

            p = p.combine(dir) as FatPath
        }
        return res
    }

    @Throws(IOException::class)
    protected fun loadClusterChain(startClusterIndex: Int): ArrayList<Int> {
        val res = ArrayList<Int>()
        var idx = startClusterIndex
        synchronized(_ioSyncer) {
            if (containerFile == null) throw FileSystemClosedException()
            try {
                while (idx > 0 && idx != LAST_CLUSTER) {
                    res.add(idx)
                    idx = getNextClusterIndex(idx)
                }
            } catch (ignored: ArrayIndexOutOfBoundsException) {
            }
        }
        return res
    }

    /*protected int getClusterIndexFromChainAt(int positionInChain, int startClusterIndex, boolean attachNew) throws IOException
  {
  	int idx = startClusterIndex;
  	for (int i = 0; i < positionInChain; i++)
  	{
  		int prev = idx;
  		idx = getNextClusterIndex(idx);
  		if (idx < 0 || idx == LAST_CLUSTER)
  		{
  			if (attachNew)
  				idx = attachFreeCluster(prev,true);
  			else
  				throw new EOFException();
  		}
  	}
  	return idx;
  }*/
    @Throws(IOException::class)
    protected open fun writeClusterIndex(clusterPosition: Int, clusterIndex: Int) {
        containerFile.seek(getClusterIndexPosition(clusterPosition).toLong())
    }

    @Throws(IOException::class)
    protected fun setNextClusterIndex(clusterPosition: Int, clusterIndex: Int, commit: Boolean) {
        if (commit) writeClusterIndex(clusterPosition, clusterIndex)
        if (clusterTable != null) clusterTable!![clusterPosition] = clusterIndex
    }

    @Throws(IOException::class)
    protected fun attachFreeCluster(lastClusterIndex: Int, commit: Boolean): Int {
        synchronized(_ioSyncer) {
            if (containerFile == null) throw FileSystemClosedException()
            val freeCluster = freeClusterIndex
            if (lastClusterIndex > 0 && lastClusterIndex != LAST_CLUSTER) setNextClusterIndex(
                lastClusterIndex,
                freeCluster,
                commit
            )
            setNextClusterIndex(freeCluster, LAST_CLUSTER, commit)
            return freeCluster
        }
    }

    @Throws(IOException::class)
    protected fun zeroCluster(clusterIndex: Int) {
        containerFile.seek(_bpb!!.getClusterOffset(clusterIndex))
        containerFile.write(_emptyCluster, 0, _emptyCluster.size)
    }

    @get:Throws(IOException::class)
    protected open val freeClusterIndex: Int
        get() {
            for (i in 2..<_totalClusterNumber) {
                if (getNextClusterIndex(i) == 0) return i
            }

            throw com.sovworks.eds.fs.errors.NoFreeSpaceLeftException()
        }

    internal inner class RootDirReader(private val length: Int, private val _startPosition: Long) :
        InputStream(),
        DirReader {
        @Throws(IOException::class)
        override fun seek(position: Long) {
            bytesRead = position.toInt()
            bytesAvail = 0
            bufferOffset = bytesAvail
        }

        override fun getFilePointer(): Long {
            return (bytesRead + bufferOffset).toLong()
        }

        @Throws(IOException::class)
        override fun length(): Long {
            return length.toLong()
        }

        @Throws(IOException::class)
        override fun read(): Int {
            if (bufferOffset == bytesAvail) fillBuffer()
            if (bytesAvail <= 0) return -1
            return (buffer[bufferOffset++].toInt() and 0xFF)
        }

        @Throws(IOException::class)
        override fun close() {
            releasePathLock(rootPath)
        }

        private var bytesRead = 0
        private val buffer = ByteArray(if (length > 1024) 1024 else length)
        private var bufferOffset = 0
        private var bytesAvail = 0

        @Throws(IOException::class)
        private fun fillBuffer() {
            synchronized(_ioSyncer) {
                if (this.containerFile == null) throw FileSystemClosedException()
                bytesRead += bytesAvail
                containerFile.seek(_startPosition + bytesRead)
                bufferOffset = 0
                bytesAvail = length - bytesRead
                if (bytesAvail <= 0) return
                if (bytesAvail > buffer.size) bytesAvail = buffer.size
                Util.readBytes(this.containerFile, buffer, bytesAvail)
            }
        }
    }

    open inner class ClusterChainIO(
        startClusterIndex: Int,
        path: Path,
        currentSize: Long,
        protected val _mode: AccessMode
    ) :
        RandomAccessIO {
        @Throws(IOException::class)
        override fun seek(position: Long) {
            if (position < 0) return
            synchronized(_rwSync) {
                if (_isBufferLoaded) {
                    val dif = position - bufferPosition
                    if (dif < 0 || dif >= _bufferSize) {
                        if (_isBufferDirty) writeBuffer()
                        _isBufferLoaded = false
                    }
                }
                _currentStreamPosition = position
            }
        }

        @Throws(IOException::class)
        override fun setLength(newLength: Long) {
            if (_mode == Read) throw IOException("The file is opened in read only mode")
            require(newLength >= 0)
            if (newLength > MAX_FILE_SIZE) throw IOException("File size is too large for FAT.")
            synchronized(_rwSync) {
                val curOffset = _currentStreamPosition
                seek(newLength)
                val clusterIndex = if (_currentStreamPosition == 0L) -1 else clusterIndexInChain
                if (clusterIndex >= _clusterChain.size) addMissingClusters(clusterIndex - _clusterChain.size + 1)
                else if (clusterIndex < _clusterChain.size - 1) removeExcessClusters(clusterIndex)
                _lastCluster = if (clusterIndex < 0) LAST_CLUSTER else _clusterChain[clusterIndex]
                _maxStreamPosition = _currentStreamPosition
                _currentStreamPosition =
                    if (curOffset > _maxStreamPosition) _maxStreamPosition else curOffset
            }
        }

        @Throws(IOException::class)
        override fun getFilePointer(): Long {
            return _currentStreamPosition
        }

        @Throws(IOException::class)
        override fun flush() {
            synchronized(_rwSync) {
                if (_isBufferDirty) writeBuffer()
                commitAddedClusters()
            }
        }

        @Throws(IOException::class)
        override fun close() {
            try {
                flush()
            } finally {
                releasePathLock(_path)
            }

            // if(LOG_MORE)
            //	Log.d("FatFs",String.format("Closed file %s. Max size:
            // %d.",_path.getPathString(),_maxStreamPosition));
        }

        @Throws(IOException::class)
        override fun write(data: Int) {
            if (_mode == Read) throw IOException("Writing disabled")

            synchronized(_rwSync) {
                _oneByteBuf[0] = (data and 0xFF).toByte()
                write(_oneByteBuf, 0, 1)
            }
        }

        @Throws(IOException::class)
        override fun write(b: ByteArray, off: Int, len: Int) {
            var off = off
            var len = len
            if (_mode == Read) throw IOException("Writing disabled")
            if (len <= 0) return

            synchronized(_rwSync) {
                if (_currentStreamPosition + len > MAX_FILE_SIZE) throw IOException("File size is too large for FAT.")
                while (len > 0) {
                    if (!_isBufferLoaded) loadBuffer()
                    val currentPositionInBuffer = positionInBuffer
                    val avail = _bufferSize - currentPositionInBuffer
                    val written = min(avail.toDouble(), len.toDouble()).toInt()
                    System.arraycopy(b, off, _buffer, currentPositionInBuffer, written)
                    _isBufferDirty = true
                    if (avail == written) {
                        writeBuffer()
                        _isBufferLoaded = false
                    }
                    off += written
                    len -= written
                    _currentStreamPosition += written.toLong()
                }
                if (_currentStreamPosition > _maxStreamPosition) _maxStreamPosition =
                    _currentStreamPosition
            }
        }

        @Throws(IOException::class)
        override fun read(): Int {
            if (_mode == Write || _mode == WriteAppend) throw IOException("The file is opened in write only mode")
            synchronized(_rwSync) {
                if (_currentStreamPosition >= _maxStreamPosition) return -1
                return if (read(_oneByteBuf, 0, 1) == 1) (_oneByteBuf[0].toInt() and 0xFF) else -1
            }
        }

        @Throws(IOException::class)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (_mode == Write || _mode == WriteAppend) throw IOException("The file is opened in write only mode")
            if (len <= 0) return 0
            synchronized(_rwSync) {
                if (!_isBufferLoaded) loadBuffer()
                val currentPositionInBuffer = positionInBuffer
                val avail = _bufferSize - currentPositionInBuffer
                val read = min(
                    min(avail.toDouble(), len.toDouble()),
                    (_maxStreamPosition - _currentStreamPosition).toDouble()
                ).toInt()
                if (read <= 0) return -1
                System.arraycopy(_buffer, currentPositionInBuffer, b, off, read)
                if (avail == read) {
                    if (_isBufferDirty) writeBuffer()
                    _isBufferLoaded = false
                }
                _currentStreamPosition += read.toLong()
                // if(LOG_MORE)
                // Log.d("EDS ClusterChainIO",String.format("ClusterChainIO read: file=%s read %d
                // bytes",_path.getPathString(),avail));
                return read
            }
        }

        @Throws(IOException::class)
        override fun length(): Long {
            return _maxStreamPosition
        }

        protected val _clusterChain: ArrayList<Int> =
            loadClusterChain(startClusterIndex)
        protected val _addedClusters: ArrayList<Int> = ArrayList()
        protected val _oneByteBuf: ByteArray = ByteArray(1)
        protected var _currentStreamPosition: Long = 0
        protected var _maxStreamPosition: Long
        protected var _lastCluster: Int
        protected val _bufferSize: Int = _bpb!!.sectorsPerCluster * _bpb!!.bytesPerSector
        protected val _buffer: ByteArray = ByteArray(_bufferSize)
        protected var _isBufferLoaded: Boolean = false
        protected var _isBufferDirty: Boolean = false
        protected val _path: Path
        protected val _rwSync: Any = Any()

        init {
            _lastCluster =
                if (_clusterChain.isEmpty()) LAST_CLUSTER else _clusterChain[_clusterChain.size - 1]
            _maxStreamPosition =
                if (currentSize < 0) (_clusterChain.size * _bufferSize).toLong() else currentSize
            _path = path
            // if(LOG_MORE)
            // Log.d("FatFs",String.format("Opened file %s. Current size:
            // %d.",_path.getPathString(),currentSize));
        }

        @Throws(IOException::class)
        protected open fun writeBuffer() {
            synchronized(_ioSyncer) {
                if (this.containerFile == null) throw FileSystemClosedException()
                try {
                    val numClusters = _clusterChain.size
                    val cluster: Int
                    val clusterIndex = clusterIndexInChain
                    if (clusterIndex < numClusters) cluster = _clusterChain[clusterIndex]
                    else if (clusterIndex == numClusters) cluster = addCluster()
                    else {
                        addMissingClusters(clusterIndex - numClusters)
                        cluster = addCluster()
                    }
                    containerFile.seek(_bpb!!.getClusterOffset(cluster))
                    containerFile.write(_buffer, 0, _bufferSize)
                } catch (e: NoFreeSpaceLeftException) {
                    _isBufferDirty = false
                    setLength((_clusterChain.size * _bufferSize).toLong())
                    throw e
                }
                _isBufferDirty = false
            }
        }

        @Throws(IOException::class)
        protected fun loadBuffer() {
            synchronized(_ioSyncer) {
                if (this.containerFile == null) throw FileSystemClosedException()
                val cluster: Int
                val clusterIndex = clusterIndexInChain
                cluster = if (clusterIndex >= _clusterChain.size) 0
                else _clusterChain[clusterIndex]

                var read = 0
                if (cluster != LAST_CLUSTER && cluster != 0) {
                    containerFile.seek(_bpb!!.getClusterOffset(cluster))
                    read = Util.readBytes(this.containerFile, _buffer)
                }
                Arrays.fill(_buffer, read, _bufferSize, 0.toByte())
                _isBufferLoaded = true
            }
        }

        private val clusterIndexInChain: Int
            get() = (_currentStreamPosition / _bufferSize).toInt()

        private val positionInBuffer: Int
            get() = (_currentStreamPosition % _bufferSize).toInt()

        private val bufferPosition: Long
            get() = _currentStreamPosition - (_currentStreamPosition % _bufferSize)

        @Throws(IOException::class)
        private fun addCluster(): Int {
            val prev = if (_clusterChain.isEmpty()) 0 else _clusterChain[_clusterChain.size - 1]
            val freeCluster = attachFreeCluster(prev, false)
            _clusterChain.add(freeCluster)
            _addedClusters.add(freeCluster)
            return freeCluster
        }

        @Throws(IOException::class)
        private fun addMissingClusters(numClusters: Int) {
            val prev = if (_clusterChain.isEmpty()) 0 else _clusterChain[_clusterChain.size - 1]
            for (i in 0..<numClusters) {
                val freeCluster = attachFreeCluster(prev, false)
                zeroCluster(freeCluster)
                _clusterChain.add(freeCluster)
                _addedClusters.add(freeCluster)
            }
        }

        @Throws(IOException::class)
        private fun removeExcessClusters(lastClusterIndex: Int) {
            synchronized(_ioSyncer) {
                for (i in _clusterChain.size - 1 downTo lastClusterIndex + 1) {
                    setNextClusterIndex(_clusterChain[i], 0, true)
                    _clusterChain.removeAt(i)
                }
            }
        }

        @Throws(IOException::class)
        private fun commitAddedClusters() {
            if (_addedClusters.isEmpty()) return
            synchronized(_ioSyncer) {
                if (this.containerFile == null) throw FileSystemClosedException()
                val numAddedClusters = _addedClusters.size
                if (_lastCluster != LAST_CLUSTER) setNextClusterIndex(
                    _lastCluster,
                    _addedClusters[0], true
                )
                for (i in 0..<numAddedClusters - 1) setNextClusterIndex(
                    _addedClusters[i],
                    _addedClusters[i + 1], true
                )
                setNextClusterIndex(_addedClusters[numAddedClusters - 1], LAST_CLUSTER, true)
                _lastCluster = _clusterChain[_clusterChain.size - 1]
                _addedClusters.clear()
                containerFile.flush()
            }
        }
    }

    internal inner class RootDirWriter(private val _length: Int, private val _startPosition: Long) :
        OutputStream(),
        DirWriter {
        @Throws(IOException::class)
        override fun seek(position: Long) {
            writeBuffer()
            _bytesWritten = position.toInt()
            _bufferOffset = 0
            _bytesAvail = _bufferOffset
        }

        override fun getFilePointer(): Long {
            return (_bytesWritten + _bufferOffset).toLong()
        }

        @Throws(IOException::class)
        override fun length(): Long {
            return _length.toLong()
        }

        @Throws(IOException::class)
        override fun write(oneByte: Int) {
            if (_bufferOffset >= _bytesAvail) writeBuffer()

            if (_bytesAvail <= 0) throw EOFException()

            _buffer[_bufferOffset++] = oneByte.toByte()
        }

        @Throws(IOException::class)
        override fun flush() {
            writeBuffer()
        }

        @Throws(IOException::class)
        override fun close() {
            try {
                flush()
            } finally {
                releasePathLock(rootPath)
            }
        }

        private val _buffer = ByteArray(if (_length > 1024) 1024 else _length)
        private var _bufferOffset = 0
        private var _bytesAvail = 0
        private var _bytesWritten = 0

        @Throws(IOException::class)
        private fun writeBuffer() {
            synchronized(_ioSyncer) {
                if (this.containerFile == null) throw FileSystemClosedException()
                containerFile.seek(_startPosition + _bytesWritten)
                containerFile.write(_buffer, 0, _bufferOffset)
            }
            _bytesWritten += _bufferOffset
            _bufferOffset = 0
            _bytesAvail = _length - _bytesWritten
            if (_bytesAvail <= 0) return
            if (_bytesAvail > _buffer.size) _bytesAvail = _buffer.size
        }
    }

    companion object {
        const val SECTOR_SIZE: Int = 512

        fun isFAT(f: RandomAccessIO): Boolean {
            val cmp = byteArrayOf('F'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte())
            val buf = ByteArray(3)
            try {
                f.seek(0x036)
                if (Util.readBytes(f, buf) == buf.size) {
                    if (!cmp.contentEquals(buf)) {
                        f.seek(0x052)
                        if (Util.readBytes(f, buf) == buf.size) return cmp.contentEquals(buf)
                    } else return true
                }
            } catch (ignored: IOException) {
            }

            return false
        }

        const val TAG: String = "FatFS"
        const val LOG_ACQUIRE: Boolean = false

        private fun logAquiring(tag: String): Int {
            if (BuildConfig.DEBUG && LOG_ACQUIRE) {
                val o = Any()
                val id = o.hashCode()
                Log.v(
                    TAG,
                    String.format(
                        "Acquiring %s. Id = %d. Current thread id = %d...",
                        tag, id, Thread.currentThread().id
                    )
                )
                Thread.dumpStack()
                return id
            }
            return 0
        }

        private fun logAquired(id: Int) {
            if (BuildConfig.DEBUG && LOG_ACQUIRE) Log.v(
                TAG,
                "$id has been acquired."
            )
        }

        private fun logReleased(id: Int) {
            if (BuildConfig.DEBUG && LOG_ACQUIRE) Log.v(
                TAG,
                "$id has been released."
            )
        }

        /**
         * Get Fat instance
         *
         * @param input disk image stream.
         * @return Fat instance
         * @throws WrongImageFormatException - if fs image file is wrong
         * @throws IOException - if io error occurs
         */
        @Throws(IOException::class)
        fun getFat(input: RandomAccessIO): FatFS {
            val fat: FatFS
            synchronized(input) {
                val bpb = BPB()
                bpb.read(input)
                val sectorsNumber =
                    if (bpb.totalSectorsNumber == 0) bpb.sectorsBig else bpb.totalSectorsNumber.toLong()
                val root_dir_sectors: Int =
                    bpb.rootDirEntries * DirEntry.Companion.RECORD_SIZE / bpb.bytesPerSector
                val data_sectors =
                    (sectorsNumber
                            - (bpb.reservedSectors
                            + bpb.numberOfFATs * bpb.getSectorsPerFat() + root_dir_sectors))
                val num_clusters = 1 + (data_sectors / bpb.sectorsPerCluster).toInt()
                fat = if (root_dir_sectors == 0) Fat32FS(input)
                else {
                    if (num_clusters < 4085) Fat12FS(input)
                    else if (num_clusters < 65525) Fat16FS(input)
                    else Fat32FS(input)
                }
                fat.init()
            }
            return fat
        }

        @Throws(IllegalArgumentException::class, IOException::class)
        fun formatFat(input: RandomAccessIO, size: Long): FatFS {
            var size = size
            require(!(size <= 0 || size > 1000000000000L)) { "Wrong size: $size" }

            val fat: FatFS
            synchronized(input) {
                val clustSize = getOptimalClusterSize(size, SECTOR_SIZE)
                val numClusters = (size / (clustSize * SECTOR_SIZE)).toInt()
                size = numClusters.toLong() * clustSize * SECTOR_SIZE

                fat = if (numClusters < 4085) Fat12FS(input)
                else if (numClusters < 65525) Fat16FS(input)
                else Fat32FS(input)

                input.seek(size - 1)
                input.write(0)

                fat.initBPB(size)
                fat.writeHeader()
                fat.writeEmptyClusterTable()
                fat.writeFatBackup()
                fat.init()

                val os = fat.getDirWriter(fat.rootPath as FatPath, Any())
                try {
                    val clusterSize = fat._bpb!!.bytesPerSector * fat._bpb!!.sectorsPerCluster
                    for (i in 0..<clusterSize) os!!.write(0)
                } finally {
                    os!!.close()
                }
            }

            return fat
        }

        fun getOptimalClusterSize(volumeSize: Long, sectorSize: Int): Int {
            var clusterSize: Int
            clusterSize = if (volumeSize >= 2 * 1024L * 1024L * 1024L * 1024L) 256 * 1024
            else if (volumeSize >= 512 * 1024L * 1024L * 1024L) 128 * 1024
            else if (volumeSize >= 128 * 1024L * 1024L * 1024L) 64 * 1024
            else if (volumeSize >= 64 * 1024L * 1024L * 1024L) 32 * 1024
            else if (volumeSize >= 32 * 1024L * 1024L * 1024L) 16 * 1024
            else if (volumeSize >= 16 * 1024L * 1024L * 1024L) 8 * 1024
            else if (volumeSize >= 512 * 1024L * 1024L) 4 * 1024
            else if (volumeSize >= 256 * 1024L * 1024L) 2 * 1024
            else if (volumeSize >= 1024L * 1024L) 1024
            else 512

            clusterSize /= sectorSize

            if (clusterSize == 0) clusterSize = 1
            else if (clusterSize > 128) clusterSize = 128

            return clusterSize
        }

        fun isValidFileNameImpl(fileName: String): Boolean {
            val tfn = fileName.trim { it <= ' ' }
            if (tfn == "" || tfn == "." || tfn.endsWith("..")) return false

            for (i in 0..<fileName.length) {
                val c = fileName[i]
                if (c.code <= 31 || RESERVED_SYMBOLS.indexOf(c) >= 0) return false
            }

            return true
        }

        protected const val MAX_FILE_SIZE: Long = 2L * Int.MAX_VALUE - 1
        protected const val RESERVED_SYMBOLS: String = "<>:\"/\\|?*"
        const val LAST_CLUSTER: Int = 0x0FFFFFFF
        protected const val MAX_DIR_ENTRIES_CACHE: Int = 10000

        private val FAT_START = byteArrayOf(
            0xeb.toByte(),
            0x3c,
            0x90.toByte(),
            0x4D.toByte(),
            0x53.toByte(),
            0x44.toByte(),
            0x4F.toByte(),
            0x53.toByte(),
            0x35.toByte(),
            0x2E.toByte(),
            0x30.toByte(),
            0x00.toByte(),
            0x02.toByte(),
            0x01.toByte()
        )

        private const val PATH_LOCK_TIMEOUT = 5000
    }
}

internal class Fat12FS(inputStream: RandomAccessIO) : FatFS(inputStream) {
    init {
        _bpb = BPB16()
        _clusterIndexSize = 12
    }

    @get:Throws(IOException::class)
    override val rootDirInputStream: DirReader?
        get() = RootDirReader(
            _bpb!!.rootDirEntries * 32,
            (_bpb!!.bytesPerSector * (_bpb!!.reservedSectors + _bpb!!.sectorsPerFat * _bpb!!.numberOfFATs)).toLong()
        )

    @get:Throws(IOException::class)
    override val rootDirOutputStream: DirWriter?
        get() = RootDirWriter(
            _bpb!!.rootDirEntries * 32,
            (_bpb!!.bytesPerSector * (_bpb!!.reservedSectors + _bpb!!.sectorsPerFat * _bpb!!.numberOfFATs)).toLong()
        )

    @Throws(IOException::class)
    override fun readNextClusterIndex(clusterIndex: Int): Int {
        super.readNextClusterIndex(clusterIndex)
        val byte_offset = (clusterIndex * _clusterIndexSize) % 8

        val res = ((Util.readWordLE(containerFile) shr byte_offset) and 0xFFF)

        return if (res == 0 || (res >= 0x002 && res <= 0xFEF)) res else LAST_CLUSTER
    }

    @Throws(IOException::class)
    override fun writeClusterIndex(clusterPosition: Int, clusterIndex: Int) {
        var clusterIndex = clusterIndex
        super.writeClusterIndex(clusterPosition, clusterIndex)
        if (clusterIndex == LAST_CLUSTER) clusterIndex = 0xFFF
        var `val` = Util.readWordLE(containerFile)
        val byteOffset = (clusterPosition * _clusterIndexSize) % 8
        `val` = if (byteOffset == 0)  // val = clusterIndex | (val & 0xF00);
            clusterIndex or (`val` and 0xF000)
        else ((clusterIndex shl 4) or (`val` and 0xF))
        super.writeClusterIndex(clusterPosition, clusterIndex)
        Util.writeWordLE(containerFile, `val`.toShort())
    }
}

internal class Fat16FS(inputStream: RandomAccessIO) : FatFS(inputStream) {
    init {
        _bpb = BPB16()
        _clusterIndexSize = 16
    }

    override fun calcSectorsPerCluster(volumeSize: Long): Int {
        return getOptimalClusterSize(volumeSize, SECTOR_SIZE)
    }

    override fun getNumClusters(volumeSize: Long): Int {
        val numClusters = super.getNumClusters(volumeSize)
        return if (numClusters <= 4085) 4086 else numClusters
    }

    @Throws(IOException::class)
    override fun readNextClusterIndex(clusterIndex: Int): Int {
        super.readNextClusterIndex(clusterIndex)
        val res = (Util.readWordLE(containerFile) and 0xFFFF)

        return if (res == 0 || (res >= 0x0002 && res <= 0xFFEF)) res else LAST_CLUSTER
    }

    @Throws(IOException::class)
    override fun writeClusterIndex(clusterPosition: Int, clusterIndex: Int) {
        var clusterIndex = clusterIndex
        super.writeClusterIndex(clusterPosition, clusterIndex)
        if (clusterIndex == LAST_CLUSTER) clusterIndex = 0xFFFF
        Util.writeWordLE(containerFile, clusterIndex.toShort())
    }

    @get:Throws(IOException::class)
    override val rootDirInputStream: DirReader?
        get() = RootDirReader(
            _bpb!!.rootDirEntries * 32,
            (_bpb!!.bytesPerSector * (_bpb!!.reservedSectors + _bpb!!.sectorsPerFat * _bpb!!.numberOfFATs)).toLong()
        )

    @get:Throws(IOException::class)
    override val rootDirOutputStream: DirWriter?
        get() = RootDirWriter(
            _bpb!!.rootDirEntries * 32,
            (_bpb!!.bytesPerSector * (_bpb!!.reservedSectors + _bpb!!.sectorsPerFat * _bpb!!.numberOfFATs)).toLong()
        )

    override fun initBPB(size: Long) {
        super.initBPB(size)

        val rootDirSize = 32 * _bpb!!.rootDirEntries
        val numSectors = size / SECTOR_SIZE
        val dataSectors =
            (numSectors
                    - (rootDirSize + _bpb!!.bytesPerSector - 1) / _bpb!!.bytesPerSector - _bpb!!.reservedSectors)
        val clusterCount =
            ((dataSectors * _bpb!!.bytesPerSector) / (_bpb!!.sectorsPerCluster * _bpb!!.bytesPerSector)).toInt()
        _bpb!!.sectorsPerFat =
            (clusterCount * 2 + _bpb!!.bytesPerSector - 1) / _bpb!!.bytesPerSector

        val label: ByteArray = BPB.Companion.FAT16_LABEL.toByteArray()
        System.arraycopy(label, 0, _bpb!!.fileSystemLabel, 0, label.size)
        _bpb!!.calcParams()
    }
}

internal class Fat32FS(inputStream: RandomAccessIO) : FatFS(inputStream) {
    @Throws(IOException::class)
    override fun close(force: Boolean) {
        synchronized(_ioSyncer) {
            try {
                if (containerFile != null && !_readOnlyMode) fsInfo.write(containerFile)
            } catch (e: IOException) {
                if (!force) throw e
            }
            super.close(force)
        }
    }

    protected var fsInfo: FSInfo

    init {
        _bpb = BPB32()
        _clusterIndexSize = 32
        fsInfo = FSInfo(_bpb as BPB32)
    }

    @Throws(IOException::class)
    override fun writeHeader() {
        super.writeHeader()
        fsInfo.freeCount = calcTotalClustersNumber()
        fsInfo.write(containerFile)
        copySectors(0, 6, 3)
    }

    @Throws(IOException::class)
    override fun writeEmptyClusterTable() {
        super.writeEmptyClusterTable()
        writeClusterIndex(2, LAST_CLUSTER)
    }

    @get:Throws(IOException::class)
    override val freeClusterIndex: Int
        get() {
            val start =
                if (fsInfo.lastAllocatedCluster >= 2 && fsInfo.lastAllocatedCluster < _totalClusterNumber)
                    fsInfo.lastAllocatedCluster
                else
                    2
            for (i in start..<_totalClusterNumber) if (getNextClusterIndex(i) == 0) {
                fsInfo.lastAllocatedCluster = i
                return i
            }

            for (i in 2..<start) if (getNextClusterIndex(i) == 0) {
                fsInfo.lastAllocatedCluster = i
                return i
            }

            throw com.sovworks.eds.fs.errors.NoFreeSpaceLeftException()
        }

    override fun getNumClusters(volumeSize: Long): Int {
        val numClusters = super.getNumClusters(volumeSize)
        return if (numClusters <= 65525) 65526 else numClusters
    }

    override fun getDataRegionSize(totalSize: Long): Long {
        val fatSize = _bpb.getSectorsPerFat() * _bpb!!.bytesPerSector
        return totalSize - fatSize * _bpb!!.numberOfFATs - _bpb!!.reservedSectors * _bpb!!.bytesPerSector
    }

    @Throws(IOException::class)
    override fun readNextClusterIndex(clusterIndex: Int): Int {
        super.readNextClusterIndex(clusterIndex)
        val res = (Util.readDoubleWordLE(containerFile) and 0x0FFFFFFFL).toInt()
        return if (res == 0 || (res >= 0x2 && res <= 0xFFFFFEF)) res else LAST_CLUSTER
    }

    @Throws(IOException::class)
    override fun writeClusterIndex(clusterPosition: Int, clusterIndex: Int) {
        var clusterIndex = clusterIndex
        super.writeClusterIndex(clusterPosition, clusterIndex)
        if (clusterIndex == LAST_CLUSTER) clusterIndex = 0x0FFFFFFF
        Util.writeDoubleWordLE(containerFile, clusterIndex)
    }

    @get:Throws(IOException::class)
    override val rootDirInputStream: DirReader?
        get() = DirInputStream(
            ClusterChainIO(
                (_bpb as BPB32).rootClusterNumber,
                rootPath,
                -1,
                Read
            )
        )

    @get:Throws(IOException::class)
    override val rootDirOutputStream: DirWriter?
        get() = DirOutputStream(
            ClusterChainIO(
                (_bpb as BPB32).rootClusterNumber,
                rootPath,
                -1,
                Write
            )
        )

    override fun calcSectorsPerCluster(volumeSize: Long): Int {
        return getOptimalClusterSize(volumeSize, SECTOR_SIZE)
        //		for(short i=4;i<=8;i*=2)
        //		{
        //			long ts = bpb.bytesPerSector*i*2000000L;
        //			if(ts>=volumeSize)
        //				return i;
        //		}
        //		for(short i=8;i<=32;i*=2)
        //		{
        //			long ts = (long)bpb.bytesPerSector*i*getMaxClustersNumbers();
        //			if(ts>=volumeSize)
        //				return i;
        //		}
        //		throw new IllegalArgumentException(String.format("Wrong volume size for fat16: %d",
        // volumeSize));
    }

    override val reservedSectorsNumber: Short
        get() = 31

    override fun initBPB(size: Long) {
        super.initBPB(size)

        val b = _bpb as BPB32?
        b!!.bytesPerSector = SECTOR_SIZE
        b.sectorsPerCluster = calcSectorsPerCluster(size)
        b.rootDirEntries = 0
        b.sectorsPerFat = 0

        val numSectors = size / SECTOR_SIZE
        // Align data area for TrueCrypt
        b.reservedSectors = 32 - 1
        do {
            b.reservedSectors++
            val dataSectors = numSectors - b.reservedSectors
            val clusterCount = ((dataSectors * _bpb!!.bytesPerSector)
                    / (_bpb!!.sectorsPerCluster * _bpb!!.bytesPerSector)).toInt()
            b.sectorsPerFat32 =
                ((clusterCount * 4 + _bpb!!.bytesPerSector - 1) / _bpb!!.bytesPerSector).toLong()
        } while (b.bytesPerSector == SECTOR_SIZE
            && ((b.reservedSectors * b.bytesPerSector
                    + b.sectorsPerFat32 * b.numberOfFATs * b.bytesPerSector)
                    % 4096
                    != 0L)
        )

        b.rootClusterNumber = 2
        b.FSInfoSector = 1
        b.bootSectorReservedCopySector = 6

        val label: ByteArray = BPB.Companion.FAT32_LABEL.toByteArray()
        System.arraycopy(label, 0, _bpb!!.fileSystemLabel, 0, label.size)
        b.calcParams()
    }
}
