package com.sovworks.eds.android.fs

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.net.Uri.Builder
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.provider.BaseColumns
import android.provider.MediaStore.Images.Media
import android.provider.OpenableColumns
import com.sovworks.eds.fs.Directory.Contents
import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.File.ProgressInfo
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.util.PFDRandomAccessIO
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.fs.util.Util
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Date

class ContentResolverFs(private val _contentResolver: ContentResolver) : FileSystem {
    @Throws(IOException::class)
    override fun getPath(pathString: String): com.sovworks.eds.fs.Path {
        return Path(Uri.parse(pathString))
    }

    override fun getRootPath(): com.sovworks.eds.fs.Path {
        return Path(Builder().build())
    }

    @Throws(IOException::class)
    override fun close(force: Boolean) {
    }

    override fun isClosed(): Boolean {
        return false
    }

    inner class Path(uri: Uri?) : com.sovworks.eds.fs.Path {
        @get:Throws(IOException::class)
        @set:Throws(IOException::class)
        var lastModified: Date
            get() {
                val cursor = queryPath()
                if (cursor != null) {
                    if (cursor.moveToFirst()) try {
                        val columnIndex =
                            cursor.getColumnIndex(Media.DATE_MODIFIED)
                        if (columnIndex >= 0) return Date(cursor.getLong(columnIndex))
                    } finally {
                        cursor.close()
                    }
                }
                if (ContentResolver.SCHEME_FILE == uri.scheme) {
                    val f = java.io.File(uri.path)
                    return Date(f.lastModified())
                }
                return Date()
            }
            set(dt) {
                val cv = ContentValues()
                cv.put(Media.DATE_MODIFIED, dt.time)
                _contentResolver.update(uri, cv, null, null)
            }

        fun queryPath(): Cursor? {
            return _contentResolver.query(uri, null, null, null, null)
        }

        override fun equals(other: Any?): Boolean {
            return this === other || (other is Path && uri == other.uri)
        }

        override fun hashCode(): Int {
            return uri.hashCode()
        }

        @Throws(IOException::class)
        override fun exists(): Boolean {
            val cursor = _contentResolver.query(uri, null, null, null, null)
            if (cursor != null) try {
                return cursor.moveToFirst()
            } finally {
                cursor.close()
            }

            if (ContentResolver.SCHEME_FILE.equals(uri.scheme, ignoreCase = true)) {
                val f = java.io.File(uri.path)
                return f.exists()
            }
            return false
        }

        @Throws(IOException::class)
        override fun isFile(): Boolean {
            return exists() && !isDirectory
        }

        @Throws(IOException::class)
        override fun isDirectory(): Boolean {
            if (ContentResolver.SCHEME_FILE.equals(uri.scheme, ignoreCase = true)) {
                val f = java.io.File(uri.path)
                return f.isDirectory
            }
            val mime = _contentResolver.getType(uri)
            return mime != null && mime.startsWith(ContentResolver.CURSOR_DIR_BASE_TYPE + "/")
        }

        override fun getFileSystem(): FileSystem {
            return this@ContentResolverFs
        }

        override fun getPathString(): String {
            return uri.toString()
        }

        override fun getPathDesc(): String {
            return getFileNameByUri(_contentResolver, uri)!!
        }

        @Throws(IOException::class)
        override fun isRootDirectory(): Boolean {
            return false
        }

        @Throws(IOException::class)
        override fun getParentPath(): com.sovworks.eds.fs.Path? {
            val pathString = uri.path ?: return null
            val pu = StringPathUtil(pathString).parentPath ?: return rootPath
            val ub = uri.buildUpon()
            ub.path(pu.toString())
            return Path(ub.build())
        }

        @Throws(IOException::class)
        override fun combine(part: String): com.sovworks.eds.fs.Path {
            val cursor = queryPath() ?: throw IOException("Can't make path")
            val columnIndex = cursor.getColumnIndex(BaseColumns._ID)
            while (cursor.moveToNext()) {
                val name = getFileNameFromCursor(cursor)
                if (name != null && name == part) {
                    val ub = uri.buildUpon()
                    ub.appendPath(cursor.getString(columnIndex))
                    return Path(ub.build())
                }
            }
            throw IOException("Can't make path")
        }

        @Throws(IOException::class)
        override fun getDirectory(): com.sovworks.eds.fs.Directory {
            return Directory(this)
        }

        @Throws(IOException::class)
        override fun getFile(): com.sovworks.eds.fs.File {
            return File(this)
        }

        override fun compareTo(another: com.sovworks.eds.fs.Path): Int {
            return uri.compareTo((another as Path).uri)
        }

        val uri: Uri = uri!!
    }

    internal inner class Directory(private val _path: Path) : com.sovworks.eds.fs.Directory {
        @Throws(IOException::class)
        override fun moveTo(newParent: com.sovworks.eds.fs.Directory) {
            throw UnsupportedOperationException()
        }

        override fun getPath(): com.sovworks.eds.fs.Path {
            return _path
        }

        @Throws(IOException::class)
        override fun getName(): String {
            return getFileNameByUri(_contentResolver, _path.uri)!!
        }

        @Throws(IOException::class)
        override fun rename(newName: String) {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun getLastModified(): Date {
            return _path.lastModified
        }

        @Throws(IOException::class)
        override fun setLastModified(dt: Date) {
            _path.lastModified = dt
        }

        @Throws(IOException::class)
        override fun delete() {
            _contentResolver.delete(_path.uri, null, null)
        }

        @Throws(IOException::class)
        override fun createDirectory(name: String): com.sovworks.eds.fs.Directory {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun createFile(name: String): com.sovworks.eds.fs.File {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun list(): Contents? {
            val cursor = _path.queryPath() ?: return null
            val columnIndex = cursor.getColumnIndex(BaseColumns._ID)
            cursor.moveToFirst()
            return object : Contents {
                @Throws(IOException::class)
                override fun close() {
                    cursor.close()
                }

                override fun iterator(): MutableIterator<com.sovworks.eds.fs.Path> {
                    return object : MutableIterator<com.sovworks.eds.fs.Path?> {
                        override fun remove() {
                        }

                        override fun next(): com.sovworks.eds.fs.Path {
                            val ub = _path.uri.buildUpon()
                            ub.appendPath(cursor.getString(columnIndex))
                            val path: Path = Path(ub.build())
                            hasNext = cursor.moveToNext()
                            return path
                        }

                        override fun hasNext(): Boolean {
                            return hasNext
                        }

                        private var hasNext = columnIndex >= 0 && cursor.moveToFirst()
                    }
                }
            }
        }

        @Throws(IOException::class)
        override fun getTotalSpace(): Long {
            return 0
        }

        @Throws(IOException::class)
        override fun getFreeSpace(): Long {
            return 0
        }
    }

    internal inner class File(private val _path: Path) : com.sovworks.eds.fs.File {
        override fun getPath(): com.sovworks.eds.fs.Path {
            return _path
        }

        @Throws(IOException::class)
        override fun getName(): String {
            return getFileNameByUri(_contentResolver, _path.uri)!!
        }

        @Throws(IOException::class)
        override fun rename(newName: String) {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun getLastModified(): Date {
            return _path.lastModified
        }

        @Throws(IOException::class)
        override fun setLastModified(dt: Date) {
            _path.lastModified = dt
        }

        @Throws(IOException::class)
        override fun delete() {
            _contentResolver.delete(_path.uri, null, null)
        }

        @Throws(IOException::class)
        override fun moveTo(newParent: com.sovworks.eds.fs.Directory) {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun getInputStream(): InputStream? {
            if (!_path.isFile) throw FileNotFoundException(_path.pathString)
            return _contentResolver.openInputStream(_path.uri)
        }

        @Throws(IOException::class)
        override fun getOutputStream(): OutputStream? {
            if (!_path.isFile) throw FileNotFoundException(_path.pathString)
            return _contentResolver.openOutputStream(_path.uri)
        }

        @Throws(IOException::class)
        override fun getRandomAccessIO(accessMode: AccessMode): RandomAccessIO {
            val pfd =
                getFileDescriptor(accessMode) ?: throw UnsupportedOperationException()
            return PFDRandomAccessIO(pfd)
        }

        @Throws(IOException::class)
        override fun getSize(): Long {
            val cursor = _path.queryPath()
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (columnIndex >= 0 && !cursor.isNull(columnIndex)) return cursor.getLong(
                            columnIndex
                        )
                    }
                } finally {
                    cursor.close()
                }
            }
            if (ContentResolver.SCHEME_FILE == _path.uri.scheme) {
                val f = java.io.File(_path.uri.path)
                return f.length()
            }
            return 0
        }

        @Throws(IOException::class)
        override fun getFileDescriptor(accessMode: AccessMode): ParcelFileDescriptor? {
            return _contentResolver.openFileDescriptor(
                _path.uri,
                Util.getStringModeFromAccessMode(accessMode)
            )
        }

        @Throws(IOException::class)
        override fun copyToOutputStream(
            output: OutputStream,
            offset: Long,
            count: Long,
            progressInfo: ProgressInfo
        ) {
            Util.copyFileToOutputStream(
                output,
                this, offset, count, progressInfo
            )
        }

        @Throws(IOException::class)
        override fun copyFromInputStream(
            input: InputStream,
            offset: Long,
            count: Long,
            progressInfo: ProgressInfo
        ) {
            Util.copyFileFromInputStream(
                input,
                this, offset, count, progressInfo
            )
        }
    }

    companion object {
        fun getFileNameByUri(cr: ContentResolver, uri: Uri): String? {
            var fileName = uri.lastPathSegment
            if (ContentResolver.SCHEME_FILE.equals(uri.scheme, ignoreCase = true)) fileName =
                uri.lastPathSegment
            else {
                val cursor = cr.query(uri, null, null, null, null)
                if (cursor != null) try {
                    if (cursor.moveToFirst()) return getFileNameFromCursor(cursor)
                } finally {
                    cursor.close()
                }
            }

            return fileName
        }

        fun getFileNameFromCursor(cursor: Cursor): String? {
            if (cursor.moveToFirst()) {
                var columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex >= 0) return cursor.getString(columnIndex)
                else {
                    columnIndex = cursor.getColumnIndex(Media.DATA) //Instead of "_data"
                    if (columnIndex >= 0) {
                        val filePathUri = Uri.parse(cursor.getString(columnIndex))
                        return filePathUri.lastPathSegment
                    }
                }
            }
            return null
        }

        fun fromSendIntent(
            intent: Intent,
            contentResolver: ContentResolver
        ): ArrayList<com.sovworks.eds.fs.Path> {
            val fs = ContentResolverFs(contentResolver)
            val paths = ArrayList<com.sovworks.eds.fs.Path>()
            val extras = intent.extras
            if (extras!!.containsKey(Intent.EXTRA_STREAM)) {
                if (Intent.ACTION_SEND == intent.action) paths.add(
                    fs.Path(
                        extras.getParcelable(
                            Intent.EXTRA_STREAM
                        )
                    )
                )
                else if (Intent.ACTION_SEND_MULTIPLE == intent.action) {
                    val s = extras.getParcelableArrayList<Parcelable>(Intent.EXTRA_STREAM)
                    if (s != null) for (uri in s) paths.add(fs.Path(uri as Uri))
                }
            }

            return paths
        }
    }
}


