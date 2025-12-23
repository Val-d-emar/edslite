package com.sovworks.eds.android.fs

import android.annotation.TargetApi
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build.VERSION_CODES
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.text.TextUtils
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.fs.Directory.Contents
import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.File.ProgressInfo
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.util.PFDRandomAccessIO
import com.sovworks.eds.fs.util.PathUtil
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.fs.util.Util
import com.sovworks.eds.settings.GlobalConfig
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Date

@TargetApi(VERSION_CODES.LOLLIPOP)
class DocumentTreeFS(private val _context: Context, rootUri: Uri?) : FileSystem {
    @Throws(IOException::class)
    override fun getPath(pathString: String): Path {
        if (pathString.isEmpty()) return rootPath
        return getPath(Uri.parse(pathString))
    }

    @Throws(IOException::class)
    fun getPath(uri: Uri): Path {
        return DocumentPath(uri)
    }

    override fun getRootPath(): Path {
        return _rootPath
    }

    @Throws(IOException::class)
    override fun close(force: Boolean) {
    }

    override fun isClosed(): Boolean {
        return false
    }

    inner class File(private var _path: DocumentPath) : com.sovworks.eds.fs.File {
        override fun getPath(): Path {
            return _path
        }

        @Throws(IOException::class)
        override fun getName(): String {
            return _path.fileName
        }

        @Throws(IOException::class)
        override fun rename(newName: String) {
            _path = _path.rename(newName)
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
            _path.delete()
        }

        @Throws(IOException::class)
        override fun moveTo(newParent: com.sovworks.eds.fs.Directory) {
            val np = Util.copyFile(this, newParent).path
            delete()
            _path = np as DocumentPath
        }

        @Throws(IOException::class)
        override fun getInputStream(): InputStream? {
            if (!_path.isFile) throw FileNotFoundException(_path.pathString)
            return _context.contentResolver.openInputStream(_path.documentUri)
        }

        @Throws(IOException::class)
        override fun getOutputStream(): OutputStream? {
            return _context.contentResolver.openOutputStream(_path.documentUri)
        }

        @Throws(IOException::class)
        override fun getRandomAccessIO(accessMode: AccessMode): RandomAccessIO {
            val pfd =
                getFileDescriptor(accessMode) ?: throw UnsupportedOperationException()
            return PFDRandomAccessIO(pfd)
        }

        @Throws(IOException::class)
        override fun getSize(): Long {
            return _path.size
        }

        @Throws(IOException::class)
        override fun getFileDescriptor(accessMode: AccessMode): ParcelFileDescriptor? {
            return _context.contentResolver.openFileDescriptor(
                _path.documentUri,
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

    inner class Directory(private var _path: DocumentPath?) : com.sovworks.eds.fs.Directory {
        @Throws(IOException::class)
        override fun moveTo(newParent: com.sovworks.eds.fs.Directory) {
            if (PathUtil.isParentDirectory(
                    _path,
                    newParent.path
                )
            ) throw IOException("Can't move the folder to its sub-folder")
            val np = Util.copyFiles(_path, newParent)
            Util.deleteFiles(_path)
            _path = np as DocumentPath
        }

        @Throws(IOException::class)
        override fun rename(newName: String) {
            _path = _path!!.rename(newName)
        }

        override fun getPath(): Path {
            return _path!!
        }

        @Throws(IOException::class)
        override fun getName(): String {
            return _path!!.fileName
        }


        @Throws(IOException::class)
        override fun getLastModified(): Date {
            return _path!!.lastModified
        }

        @Throws(IOException::class)
        override fun setLastModified(dt: Date) {
            _path!!.lastModified = dt
        }

        @Throws(IOException::class)
        override fun delete() {
            _path!!.delete()
        }


        @Throws(IOException::class)
        override fun createDirectory(name: String): com.sovworks.eds.fs.Directory {
            val uri = DocumentsContract.createDocument(
                _context.contentResolver,
                _path!!.documentUri,
                Document.MIME_TYPE_DIR,
                name
            )
            if (uri == null) throw IOException("Failed creating folder")
            return Directory(DocumentPath(uri))
        }

        @Throws(IOException::class)
        override fun createFile(name: String): com.sovworks.eds.fs.File {
            val mimeType = FileOpsService.getMimeTypeFromExtension(
                _context,
                StringPathUtil(name).fileExtension
            )
            val uri = DocumentsContract.createDocument(
                _context.contentResolver,
                _path!!.documentUri,
                mimeType, name
            )
            if (uri == null) throw IOException("Failed creating file")
            return File(DocumentPath(uri))
        }

        @Throws(IOException::class)
        override fun list(): Contents {
            val uri = _path!!.documentUri
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                uri,
                DocumentsContract.getDocumentId(uri)
            )
            val resolver = _context.contentResolver
            val cursor = resolver.query(
                childrenUri,
                arrayOf(Document.COLUMN_DOCUMENT_ID),
                Document.COLUMN_DOCUMENT_ID + "!=?",
                arrayOf(".android_secure"),
                null
            )
            return object : Contents {
                @Throws(IOException::class)
                override fun close() {
                    cursor?.close()
                }

                override fun iterator(): MutableIterator<Path> {
                    return object : MutableIterator<Path?> {
                        override fun remove() {
                        }

                        override fun next(): Path {
                            if (cursor == null) throw NoSuchElementException()
                            val documentId = cursor.getString(0)
                            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                                uri,
                                documentId
                            )
                            _hasNext = cursor.moveToNext()
                            val newPath: Path = DocumentPath(documentUri)
                            synchronized(_parentsCache) {
                                _parentsCache.put(newPath, _path)
                            }
                            return newPath
                        }

                        override fun hasNext(): Boolean {
                            return _hasNext
                        }

                        private var _hasNext = cursor != null && cursor.moveToFirst()
                    }
                }
            }
        }

        @Throws(IOException::class)
        override fun getTotalSpace(): Long {
            return freeSpace
        }

        @Throws(IOException::class)
        override fun getFreeSpace(): Long {
            return _rootPath.bytesAvailable
        }
    }

    inner class DocumentPath(@get:Throws(IOException::class) val documentUri: Uri) : Path {
        override fun getPathDesc(): String {
            return fileName
        }

        @get:Throws(IOException::class)
        @set:Throws(IOException::class)
        var lastModified: Date
            get() = Date(
                queryForLong(
                    documentUri,
                    Document.COLUMN_LAST_MODIFIED,
                    0
                )
            )
            set(dt) {
                val resolver = _context.contentResolver
                val cv = ContentValues()
                cv.put(Document.COLUMN_LAST_MODIFIED, dt.time)
                try {
                    if (resolver.update(
                            documentUri,
                            cv,
                            null,
                            null
                        ) == 0
                    ) throw IOException("Failed setting last modified time")
                } catch (e: UnsupportedOperationException) {
                    throw IOException("Failed setting last modified time", e)
                }
            }

        @get:Throws(IOException::class)
        val bytesAvailable: Long
            get() = queryForLong(documentUri, Root.COLUMN_AVAILABLE_BYTES, 0)

        @get:Throws(IOException::class)
        val size: Long
            get() = queryForLong(documentUri, Document.COLUMN_SIZE, 0)

        @Throws(IOException::class)
        fun delete() {
            if (!DocumentsContract.deleteDocument(
                    _context.contentResolver,
                    documentUri
                )
            ) throw IOException("Delete failed")
        }

        @Throws(IOException::class)
        fun rename(newName: String): DocumentPath {
            try {
                var p = parentPath
                if (p != null) {
                    p = p.combine(newName)
                    if (p.exists()) p.file.delete()
                }
            } catch (ignored: IOException) {
            }

            val newUri = DocumentsContract.renameDocument(
                _context.contentResolver,
                documentUri, newName
            )
            if (newUri == null) throw IOException("Rename failed")
            else return DocumentPath(newUri)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other is DocumentPath) {
                return documentUri == other.documentUri
            }
            return false
        }

        override fun hashCode(): Int {
            return documentUri.hashCode()
        }

        @Throws(IOException::class)
        override fun exists(): Boolean {
            var c: Cursor? = null
            try {
                c = _context.contentResolver.query(
                    documentUri,
                    arrayOf(Document.COLUMN_DOCUMENT_ID),
                    null,
                    null,
                    null
                )
                return c != null && c.count > 0
            } catch (e: Exception) {
                if (GlobalConfig.isDebug()) Logger.log(e)
            } finally {
                closeQuietly(c)
            }
            return false
        }

        override fun toString(): String {
            return pathString
        }

        @Throws(IOException::class)
        override fun isFile(): Boolean {
            try {
                val type = rawType
                return !(Document.MIME_TYPE_DIR == type || TextUtils.isEmpty(type))
            } catch (e: Exception) {
                return false
            }
        }

        @Throws(IOException::class)
        override fun isDirectory(): Boolean {
            return try {
                isRootDirectory || Document.MIME_TYPE_DIR == rawType
            } catch (e: Exception) {
                false
            }
        }

        override fun getFileSystem(): FileSystem {
            return this@DocumentTreeFS
        }

        override fun getPathString(): String {
            return documentUri.toString()
        }

        val fileName: String
            get() {
                try {
                    return queryForString(
                        documentUri,
                        Document.COLUMN_DISPLAY_NAME,
                        "unknown"
                    )
                } catch (e: IOException) {
                    if (GlobalConfig.isDebug()) Logger.log(e)
                    return pathString
                }
            }

        @Throws(IOException::class)
        override fun getParentPath(): Path? {
            return this@DocumentTreeFS.getParentPath(this)
        }

        @Throws(IOException::class)
        override fun isRootDirectory(): Boolean {
            return _rootPath.documentUri == documentUri
        }

        @Throws(IOException::class)
        override fun combine(part: String): Path {
            val childUri = resolveDocumentUri(part) ?: throw FileNotFoundException()
            val newPath: Path = DocumentPath(childUri)
            synchronized(_parentsCache) {
                _parentsCache.put(newPath, this)
            }
            return newPath
        }

        @Throws(IOException::class)
        override fun getDirectory(): com.sovworks.eds.fs.Directory {
            return Directory(this)
        }

        @Throws(IOException::class)
        override fun getFile(): com.sovworks.eds.fs.File {
            return File(this)
        }

        override fun compareTo(another: Path): Int {
            return documentUri.compareTo((another as DocumentPath).documentUri)
        }

        private inner class ChildUriReceiver(private val _childName: String) : ResultReceiver {
            override fun nextResult(c: Cursor): Boolean {
                if (!c.isNull(1) && _childName == c.getString(1)) {
                    val documentId = c.getString(0)
                    childUri = DocumentsContract.buildDocumentUriUsingTree(
                        this.pathUri,
                        documentId
                    )
                    return false
                }
                return true
            }

            var childUri: Uri? = null
                private set
        }

        @Synchronized
        private fun resolveDocumentUri(childName: String): Uri? {
            val rec = ChildUriReceiver(childName)
            listChildren(
                rec,
                documentUri,
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME
            )
            return rec.childUri
        }

        private fun listChildren(res: ResultReceiver, uri: Uri, vararg columns: String) {
            val resolver = _context.contentResolver
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                uri,
                DocumentsContract.getDocumentId(uri)
            )
            var c: Cursor? = null
            try {
                c = resolver.query(childrenUri, columns, null, null, null)
                if (c != null) while (c.moveToNext()) {
                    if (!res.nextResult(c)) break
                }
            } catch (e: Exception) {
                if (GlobalConfig.isDebug()) Logger.log(e)
            } finally {
                closeQuietly(c)
            }
        }

        @get:Throws(IOException::class)
        private val rawType: String
            get() = queryForString(
                documentUri,
                Document.COLUMN_MIME_TYPE,
                null
            )

        private fun queryForLong(uri: Uri, column: String, defaultValue: Long): Long {
            val resolver = _context.contentResolver
            var c: Cursor? = null
            try {
                c = resolver.query(uri, arrayOf(column), null, null, null)
                return if (c != null && c.moveToFirst() && !c.isNull(0)) c.getLong(0)
                else defaultValue
            } catch (e: Exception) {
                if (GlobalConfig.isDebug()) Logger.log(e)
                return defaultValue
            } finally {
                closeQuietly(c)
            }
        }

        private fun queryForString(uri: Uri, column: String, defaultValue: String): String {
            val resolver = _context.contentResolver
            var c: Cursor? = null
            try {
                c = resolver.query(uri, arrayOf(column), null, null, null)
                return if (c != null && c.moveToFirst() && !c.isNull(0)) {
                    c.getString(0)
                } else {
                    defaultValue
                }
            } catch (e: Exception) {
                if (GlobalConfig.isDebug()) Logger.log(e)
                return defaultValue
            } finally {
                closeQuietly(c)
            }
        }
    }

    private interface ResultReceiver {
        fun nextResult(c: Cursor): Boolean
    }

    private val _rootPath = DocumentPath(
        DocumentsContract.buildDocumentUriUsingTree(
            rootUri,
            DocumentsContract.getTreeDocumentId(rootUri)
        )
    )
    private val _parentsCache: MutableMap<Path, Path?> = HashMap()

    @Throws(IOException::class)
    private fun getParentPath(path: Path): Path? {
        if (path.isRootDirectory) return null
        synchronized(_parentsCache) {
            var parentPath = _parentsCache[path]
            if (parentPath == null) {
                parentPath = findParentPath(_rootPath, path)
                if (parentPath == null) throw IOException("Couldn't find parent path for " + path.pathString)
                _parentsCache[path] = parentPath
            }
            return parentPath
        }
    }

    @Throws(IOException::class)
    private fun findParentPath(startSearchPath: Path, targetPath: Path): Path? {
        if (!startSearchPath.isDirectory) return null
        val dc = startSearchPath.directory.list()
        try {
            for (p in dc) {
                if (p == targetPath) return startSearchPath
                if (p.isDirectory) {
                    val res = findParentPath(p, targetPath)
                    if (res != null) return res
                }
            }
        } finally {
            dc.close()
        }
        return null
    }

    companion object {
        private fun closeQuietly(closeable: AutoCloseable?) {
            if (closeable != null) {
                try {
                    closeable.close()
                } catch (rethrown: RuntimeException) {
                    throw rethrown
                } catch (ignored: Exception) {
                }
            }
        }
    }
}


