package com.sovworks.eds.android.providers.cursor

import android.annotation.TargetApi
import android.content.Context
import android.database.AbstractCursor
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.provider.DocumentsContract.Root
import android.text.format.Formatter
import com.drew.lang.annotations.NotNull
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.providers.ContainersDocumentProviderBase
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.schedulers.Schedulers
import java.io.IOException

@TargetApi(VERSION_CODES.KITKAT)
class DocumentRootsCursor(
    private val _context: Context?,
    private val _lm: LocationsManager,
    @param:NotNull private val _projection: Array<String>
) :
    AbstractCursor() {
    override fun getCount(): Int {
        return _locations.size
    }

    override fun getColumnNames(): Array<String> {
        return _projection
    }

    override fun getString(column: Int): String {
        return getValueFromCurrentLocation(column).toString()
    }

    override fun getShort(column: Int): Short {
        return getValueFromCurrentLocation(column) as Short
    }

    override fun getInt(column: Int): Int {
        return getValueFromCurrentLocation(column) as Int
    }

    override fun getLong(column: Int): Long {
        return getValueFromCurrentLocation(column) as Long
    }

    override fun getFloat(column: Int): Float {
        return getValueFromCurrentLocation(column) as Float
    }

    override fun getDouble(column: Int): Double {
        return getValueFromCurrentLocation(column) as Double
    }

    override fun isNull(column: Int): Boolean {
        return getValueFromCurrentLocation(column) == null
    }

    override fun onMove(oldPosition: Int, newPosition: Int): Boolean {
        _current = null
        if (newPosition >= 0 && newPosition < _locations.size) _current = getObservable(
            _locations[newPosition]
        )!!.subscribeOn
        (Schedulers.io()).blockingGet
        ()
        return _current != null
    }

    override fun requery(): Boolean {
        fillList()
        _current = null
        return super.requery()
    }

    private val _locations: MutableList<EDSLocation> = ArrayList()

    private class LocationInfo {
        var location: Location? = null
        var freeSpace: Long = 0
        var totalSpace: Long = 0
        var title: String? = null
        var documentId: String? = null
    }

    private var _request: Single<LocationInfo>? = null
    private var _current: LocationInfo? = null

    init {
        fillList()
    }

    private fun fillList() {
        _locations.clear()
        for (l in _lm.getLoadedEDSLocations(true)) if (l.isOpen) _locations.add(l)
    }

    private fun getObservable(loc: EDSLocation): Single<LocationInfo>? {
        synchronized(this) {
            if (_request == null) try {
                _request = createObservable(loc)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
            return _request
        }
    }

    @Throws(Exception::class)
    private fun createObservable(loc: EDSLocation): Single<LocationInfo> {
        return Single.create<LocationInfo> { em: SingleEmitter<LocationInfo> ->
            val res = LocationInfo()
            res.location = loc
            try {
                res.freeSpace = loc.fs.rootPath.directory.freeSpace
            } catch (e: IOException) {
                Logger.log(e)
            }
            try {
                res.totalSpace = loc.fs.rootPath.directory.totalSpace
            } catch (e: IOException) {
                Logger.log(e)
            }
            res.title = loc.title
            val tmp: Location = loc.copy()
            tmp.currentPath = loc.fs.rootPath
            res.documentId = ContainersDocumentProviderBase.Companion.getDocumentIdFromLocation(tmp)
            em.onSuccess(res)
        }
    }

    private fun getValueFromCurrentLocation(column: Int): Any? {
        if (_current == null) return null
        return getValueFromCachedPathInfo(_current!!, _projection[column])
    }

    private fun getValueFromCachedPathInfo(li: LocationInfo, columnName: String): Any? {
        when (columnName) {
            Root.COLUMN_ROOT_ID -> return li.location!!.id
            Root.COLUMN_SUMMARY -> return _context!!.getString(
                R.string.container_info_summary,
                Formatter.formatFileSize(_context, li.freeSpace),
                Formatter.formatFileSize(_context, li.totalSpace)
            )

            Root.COLUMN_FLAGS -> return getFlags(li)
            Root.COLUMN_TITLE -> return li.title
            Root.COLUMN_DOCUMENT_ID -> return li.documentId
            Root.COLUMN_MIME_TYPES -> return "*/*"
            Root.COLUMN_AVAILABLE_BYTES -> return li.freeSpace
            Root.COLUMN_ICON -> return R.drawable.ic_lock_open
            else -> if (VERSION.SDK_INT >= VERSION_CODES.N) return getMoreColumns(li, columnName)

        }
        return null
    }

    private fun getMoreColumns(li: LocationInfo, columnName: String): Any? {
        when (columnName) {
            Root.COLUMN_CAPACITY_BYTES -> return li.totalSpace
        }
        return null
    }


    private fun getFlags(li: LocationInfo): Int {
        // FLAG_SUPPORTS_CREATE means at least one directory under the root supports
        // creating documents. FLAG_SUPPORTS_RECENTS means your application's most
        // recently used documents will show up in the "Recents" category.
        // FLAG_SUPPORTS_SEARCH allows users to search all documents the application
        // shares.
        var flags = Root.FLAG_SUPPORTS_SEARCH
        if (!li.location!!.isReadOnly) flags = flags or Root.FLAG_SUPPORTS_CREATE
        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) flags = flags or Root.FLAG_SUPPORTS_IS_CHILD
        return flags
    }
}
