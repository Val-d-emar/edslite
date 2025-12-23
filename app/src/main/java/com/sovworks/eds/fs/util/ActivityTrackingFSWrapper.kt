package com.sovworks.eds.fs.util

import android.os.SystemClock
import com.sovworks.eds.fs.Directory.Contents
import com.sovworks.eds.fs.FSRecord
import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.RandomAccessIO
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

open class ActivityTrackingFSWrapper(baseFs: FileSystem) : FileSystemWrapper(baseFs) {
    interface ChangeListener {
        @Throws(IOException::class)
        fun beforeRemoval(p: com.sovworks.eds.fs.Path?)

        fun afterRemoval(p: com.sovworks.eds.fs.Path?)

        @Throws(IOException::class)
        fun beforeModification(p: com.sovworks.eds.fs.Path?)

        fun afterModification(p: com.sovworks.eds.fs.Path?)
    }

    @Throws(IOException::class)
    override fun getRootPath(): com.sovworks.eds.fs.Path {
        return Path(base.rootPath)
    }

    @Throws(IOException::class)
    override fun getPath(pathString: String): com.sovworks.eds.fs.Path {
        return Path(base.getPath(pathString))
    }

    open fun setChangesListener(l: ChangeListener?) {
        _changesListener = l
    }

    protected inner class Path(path: com.sovworks.eds.fs.Path) :
        PathWrapper(this@ActivityTrackingFSWrapper, path) {
        @Throws(IOException::class)
        override fun getFile(): com.sovworks.eds.fs.File {
            return File(this, base.file)
        }

        @Throws(IOException::class)
        override fun getDirectory(): com.sovworks.eds.fs.Directory {
            return Directory(this, base.directory)
        }

        @Throws(IOException::class)
        override fun getPathFromBasePath(basePath: com.sovworks.eds.fs.Path?): com.sovworks.eds.fs.Path? {
            return this@ActivityTrackingFSWrapper.getPathFromBasePath(basePath)
        }
    }

    protected inner class File(path: Path, base: com.sovworks.eds.fs.File) :
        FileWrapper(path, base) {
        @Throws(IOException::class)
        override fun moveTo(newParent: com.sovworks.eds.fs.Directory) {
            val srcPath = path
            beforeMove(this, newParent)
            super.moveTo(newParent)
            afterMove(srcPath, this)
        }

        @Throws(IOException::class)
        override fun getPathFromBasePath(basePath: com.sovworks.eds.fs.Path?): com.sovworks.eds.fs.Path? {
            return this@ActivityTrackingFSWrapper.getPathFromBasePath(basePath)
        }

        @Throws(IOException::class)
        override fun delete() {
            beforeDelete(this)
            super.delete()
            afterDelete(this)
        }

        @Throws(IOException::class)
        override fun getInputStream(): InputStream {
            val `in` = super.getInputStream()
            return object : FilterInputStream(`in`) {
                @Throws(IOException::class)
                override fun read(): Int {
                    this.lastActivityTime = SystemClock.elapsedRealtime()
                    return `in`!!.read()
                }

                @Throws(IOException::class)
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    this.lastActivityTime = SystemClock.elapsedRealtime()
                    return `in`!!.read(b, off, len)
                }
            }
        }

        @Throws(IOException::class)
        override fun getOutputStream(): OutputStream {
            val out = super.getOutputStream()
            return object : FilterOutputStream(out) {
                @Throws(IOException::class)
                override fun write(b: Int) {
                    this.lastActivityTime = SystemClock.elapsedRealtime()
                    if (!_isChanged && _changesListener != null) _changesListener!!.beforeModification(
                        path
                    )
                    out!!.write(b)
                    _isChanged = true
                }

                @Throws(IOException::class)
                override fun write(b: ByteArray, off: Int, len: Int) {
                    this.lastActivityTime = SystemClock.elapsedRealtime()
                    if (!_isChanged && _changesListener != null) _changesListener!!.beforeModification(
                        path
                    )
                    out!!.write(b, off, len)
                    _isChanged = true
                }

                @Throws(IOException::class)
                override fun close() {
                    super.close()
                    if (_changesListener != null && _isChanged) _changesListener!!.afterModification(
                        path
                    )
                }

                private var _isChanged = false
            }
        }

        @Throws(IOException::class)
        override fun getRandomAccessIO(accessMode: AccessMode): RandomAccessIO {
            return ActivityTrackingFileIO(super.getRandomAccessIO(accessMode), path)
        }
    }

    protected inner class Directory(path: Path, base: com.sovworks.eds.fs.Directory) :
        DirectoryWrapper(path, base) {
        @Throws(IOException::class)
        override fun moveTo(newParent: com.sovworks.eds.fs.Directory) {
            val srcPath = path
            beforeMove(this, newParent)
            super.moveTo(newParent)
            afterMove(srcPath, this)
        }

        @Throws(IOException::class)
        override fun getPathFromBasePath(basePath: com.sovworks.eds.fs.Path?): com.sovworks.eds.fs.Path? {
            return this@ActivityTrackingFSWrapper.getPathFromBasePath(basePath)
        }

        @Throws(IOException::class)
        override fun delete() {
            beforeDelete(this)
            super.delete()
            afterDelete(this)
        }

        @Throws(IOException::class)
        override fun createFile(name: String): com.sovworks.eds.fs.File {
            this.lastActivityTime = SystemClock.elapsedRealtime()
            val f = super.createFile(name)
            if (_changesListener != null) _changesListener!!.afterModification(f!!.path)
            return f!!
        }

        @Throws(IOException::class)
        override fun createDirectory(name: String): com.sovworks.eds.fs.Directory {
            this.lastActivityTime = SystemClock.elapsedRealtime()
            val f = super.createDirectory(name)
            if (_changesListener != null) _changesListener!!.afterModification(f!!.path)
            return f!!
        }

        @Throws(IOException::class)
        override fun list(): Contents {
            this.lastActivityTime = SystemClock.elapsedRealtime()
            return super.list()
        }
    }

    protected inner class ActivityTrackingFileIO(
        base: RandomAccessIO?,
        private val _path: com.sovworks.eds.fs.Path
    ) :
        RandomAccessIOWrapper(base) {
        @Throws(IOException::class)
        override fun read(): Int {
            this.lastActivityTime = SystemClock.elapsedRealtime()
            return super.read()
        }

        @Throws(IOException::class)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            this.lastActivityTime = SystemClock.elapsedRealtime()
            return super.read(b, off, len)
        }

        @Throws(IOException::class)
        override fun write(b: Int) {
            this.lastActivityTime = SystemClock.elapsedRealtime()
            if (!_isChanged && _changesListener != null) _changesListener!!.beforeModification(_path)
            super.write(b)
            _isChanged = true
        }

        @Throws(IOException::class)
        override fun write(b: ByteArray, off: Int, len: Int) {
            this.lastActivityTime = SystemClock.elapsedRealtime()
            if (!_isChanged && _changesListener != null) _changesListener!!.beforeModification(_path)
            super.write(b, off, len)
            _isChanged = true
        }

        @Throws(IOException::class)
        override fun close() {
            super.close()
            if (_changesListener != null && _isChanged) _changesListener!!.afterModification(_path)
        }

        private var _isChanged = false
    }

    var lastActivityTime: Long
        protected set

    @Throws(IOException::class)
    protected fun getPathFromBasePath(basePath: com.sovworks.eds.fs.Path?): com.sovworks.eds.fs.Path? {
        return if (basePath == null) null else Path(basePath)
    }

    private var _changesListener: ChangeListener? = null

    init {
        lastActivityTime = SystemClock.elapsedRealtime()
    }

    @Throws(IOException::class)
    private fun beforeMove(srcRecord: FSRecord, newParent: com.sovworks.eds.fs.Directory) {
        lastActivityTime = SystemClock.elapsedRealtime()
        if (_changesListener != null) {
            _changesListener!!.beforeRemoval(srcRecord.path)
            var dst = try {
                newParent.path.combine(srcRecord.name)
            } catch (e: IOException) {
                null
            }
            if (dst != null) _changesListener!!.beforeModification(dst)
        }
    }

    @Throws(IOException::class)
    private fun afterMove(srcPath: com.sovworks.eds.fs.Path, srcRecord: FSRecordWrapper) {
        if (_changesListener != null) {
            _changesListener!!.afterRemoval(srcPath)
            _changesListener!!.afterModification(srcRecord.path)
        }
    }

    @Throws(IOException::class)
    private fun beforeDelete(srcRecord: FSRecord) {
        lastActivityTime = SystemClock.elapsedRealtime()
        if (_changesListener != null) _changesListener!!.beforeRemoval(srcRecord.path)
    }

    private fun afterDelete(srcRecord: FSRecord) {
        if (_changesListener != null) _changesListener!!.afterRemoval(srcRecord.path)
    }
}
