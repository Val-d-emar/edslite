package com.sovworks.eds.android.filemanager.records

import android.content.Context
import com.sovworks.eds.android.R
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.android.helpers.TempFilesMonitor
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.Openable
import com.sovworks.eds.settings.Settings
import com.sovworks.eds.settings.SettingsCommon
import java.io.IOException

abstract class ExecutableFileRecordBase(context: Context?) : FileRecord(context) {
    @Throws(IOException::class)
    override fun init(location: Location?, path: Path?) {
        super.init(location, path)
        _loc = location
    }

    @Throws(Exception::class)
    override fun open(): Boolean {
        if (!isFile) return false
        val mime =
            FileOpsService.getMimeTypeFromExtension(_host, StringPathUtil(name).fileExtension)
        if (mime.startsWith("image/")) openImageFile(_loc!!, this, false)
        else startDefaultFileViewer(_loc!!, this)
        return true
    }

    @Throws(Exception::class)
    override fun openInplace(): Boolean {
        if (!isFile) return false

        val mime =
            FileOpsService.getMimeTypeFromExtension(_host, StringPathUtil(name).fileExtension)
        if (mime.startsWith("image/")) {
            openImageFile(_loc!!, this, true)
            return true
        }
        _host!!.showProperties(this, true)
        return open()
    }

    protected var _loc: Location? = null
    protected val _settings: Settings = UserSettings.getSettings(context)

    @Throws(UserException::class, IOException::class)
    protected fun extractFileAndStartViewer(location: Location, rec: BrowserRecord) {
        if (rec.size > 1024 * 1024 * _settings.maxTempFileSize) throw UserException(
            _host,
            R.string.err_temp_file_is_too_big
        )
        val loc = location.copy()
        loc.currentPath = rec.path
        TempFilesMonitor.getMonitor(_context).startFile(loc)
    }

    @Throws(IOException::class, UserException::class, ApplicationException::class)
    protected fun startDefaultFileViewer(location: Location, rec: BrowserRecord) {
        val devUri = location.getDeviceAccessibleUri(rec.path)
        if (devUri != null) FileOpsService.startFileViewer(
            _host,
            devUri,
            FileOpsService.getMimeTypeFromExtension(
                _context,
                StringPathUtil(rec.name).fileExtension
            )
        )
        else extractFileAndStartViewer(location, rec)
    }

    @Throws(IOException::class, UserException::class, ApplicationException::class)
    protected fun openImageFile(location: Location, rec: BrowserRecord, inplace: Boolean) {
        val ivMode = _settings.internalImageViewerMode
        if (ivMode == SettingsCommon.USE_INTERNAL_IMAGE_VIEWER_ALWAYS ||
            (ivMode == SettingsCommon.USE_INTERNAL_IMAGE_VIEWER_VIRT_FS &&
                    location is Openable)
        ) _host!!.showPhoto(rec, inplace)
        else {
            if (inplace) _host!!.showPhoto(rec, true)
            startDefaultFileViewer(location, rec)
        }
    }
}