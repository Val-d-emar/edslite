package com.sovworks.eds.android.providers.cursor

import android.annotation.TargetApi
import android.content.Context
import android.database.AbstractCursor
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.provider.BaseColumns
import android.provider.DocumentsContract.Document
import android.provider.MediaStore.MediaColumns
import android.provider.OpenableColumns
import com.drew.lang.annotations.NotNull
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.helpers.CachedPathInfo
import com.sovworks.eds.android.providers.ContainersDocumentProviderBase
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.locations.Location
import io.reactivex.Observable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers

abstract class FSCursorBase(
    private val _context: Context,
    @JvmField val _location: Location,
    @param:NotNull private val _projection: Array<String>,
    @JvmField val _selection: String,
    @JvmField val _selectionArgs: Array<String>,
    @JvmField val _listDir: Boolean
) :
    AbstractCursor() {
    override fun getCount(): Int {
        val res = IntArray(1)
        observable!!.subscribeOn
        (Schedulers.io()).blockingForEach
        (Consumer { info: CachedPathInfo? -> res[0]++ })
        return res[0]
    }

    override fun getColumnNames(): Array<String> {
        return _projection
    }

    override fun getString(column: Int): String {
        return getValueFromCurrentCPI(column).toString()
    }

    override fun getShort(column: Int): Short {
        return getValueFromCurrentCPI(column) as Short
    }

    override fun getInt(column: Int): Int {
        return getValueFromCurrentCPI(column) as Int
    }

    override fun getLong(column: Int): Long {
        return getValueFromCurrentCPI(column) as Long
    }

    override fun getFloat(column: Int): Float {
        return getValueFromCurrentCPI(column) as Float
    }

    override fun getDouble(column: Int): Double {
        return getValueFromCurrentCPI(column) as Double
    }

    override fun isNull(column: Int): Boolean {
        return getValueFromCurrentCPI(column) == null
    }

    override fun onMove(oldPosition: Int, newPosition: Int): Boolean {
        try {
            _current = observable.elementAt
            (newPosition.toLong()).subscribeOn
            (Schedulers.io()).blockingGet
            ()
        } catch (e: Exception) {
            Logger.log(e)
            _current = null
        }
        return _current != null
    }

    private var _request: Observable<CachedPathInfo>? = null
    private var _current: CachedPathInfo? = null

    private val observable: Observable<CachedPathInfo>?
        get() {
            synchronized(this) {
                if (_request == null) try {
                    _request = createObservable()
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
                return _request
            }
        }

    @Throws(Exception::class)
    protected abstract fun createObservable(): Observable<CachedPathInfo>?

    private fun getValueFromCurrentCPI(column: Int): Any? {
        if (_current == null) return null
        return getValueFromCachedPathInfo(_current!!, _projection[column])
    }

    private fun getValueFromCachedPathInfo(cpi: CachedPathInfo, columnName: String): Any? {
        when (columnName) {
            COLUMN_ID -> return cpi.path!!.pathString.hashCode() as Long
            COLUMN_NAME, COLUMN_TITLE -> return cpi.name
            COLUMN_IS_FOLDER -> return cpi.isDirectory
            COLUMN_LAST_MODIFIED -> return cpi.modificationDate!!.time
            COLUMN_SIZE -> return cpi.size
            COLUMN_PATH -> return cpi.path!!.pathString
            else -> if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) return getDocumentValue(
                cpi,
                columnName
            )

        }
        return null
    }

    private fun getDocumentValue(cpi: CachedPathInfo, columnName: String): Any? {
        when (columnName) {
            Document.COLUMN_DISPLAY_NAME -> return cpi.name
            Document.COLUMN_DOCUMENT_ID -> {
                val tmp = _location.copy()
                tmp.currentPath = cpi.path
                return ContainersDocumentProviderBase.Companion.getDocumentIdFromLocation(tmp)
            }

            Document.COLUMN_FLAGS -> return getDocumentFlags(cpi)
            Document.COLUMN_LAST_MODIFIED -> return cpi.modificationDate!!.time
            Document.COLUMN_MIME_TYPE -> return getDocumentMimeType(cpi)
            Document.COLUMN_SIZE -> return cpi.size
        }
        return null
    }

    @TargetApi(VERSION_CODES.KITKAT)
    private fun getDocumentFlags(cpi: CachedPathInfo): Int {
        val ro = _location.isReadOnly
        var flags = 0
        if (!ro) {
            if (cpi.isFile) flags = flags or Document.FLAG_SUPPORTS_WRITE
            else if (cpi.isDirectory) flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
            flags = flags or Document.FLAG_SUPPORTS_DELETE
            if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) flags =
                flags or Document.FLAG_SUPPORTS_RENAME
            if (VERSION.SDK_INT >= VERSION_CODES.N) flags = flags or (Document.FLAG_SUPPORTS_COPY or
                    Document.FLAG_SUPPORTS_MOVE)
        }
        return flags
    }

    @TargetApi(VERSION_CODES.KITKAT)
    private fun getDocumentMimeType(cpi: CachedPathInfo): String {
        return if (cpi.isFile) FileOpsService.getMimeTypeFromExtension(
            _context,
            StringPathUtil(cpi.name).fileExtension
        ) else Document.MIME_TYPE_DIR
    }


    companion object {
        const val COLUMN_ID: String = BaseColumns._ID
        const val COLUMN_NAME: String = OpenableColumns.DISPLAY_NAME
        const val COLUMN_TITLE: String = MediaColumns.TITLE
        const val COLUMN_SIZE: String = OpenableColumns.SIZE
        const val COLUMN_LAST_MODIFIED: String = MediaColumns.DATE_MODIFIED
        const val COLUMN_IS_FOLDER: String = "is_folder"
        const val COLUMN_PATH: String = "path"
    }
}
