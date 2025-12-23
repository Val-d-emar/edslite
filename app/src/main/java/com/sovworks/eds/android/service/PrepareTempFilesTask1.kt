package com.sovworks.eds.android.service

import android.content.Context
import android.content.Intent
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.helpers.TempFilesMonitor.Companion.deleteRecWithWiping
import com.sovworks.eds.android.helpers.TempFilesMonitor.Companion.getMonitor
import com.sovworks.eds.android.helpers.TempFilesMonitor.Companion.getTmpLocation
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.FSRecord
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.SrcDstCollection
import com.sovworks.eds.fs.util.SrcDstCollection.SrcDst
import com.sovworks.eds.fs.util.SrcDstGroup
import com.sovworks.eds.fs.util.SrcDstRec
import com.sovworks.eds.fs.util.SrcDstSingle
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.settings.Settings
import java.io.IOException

internal open class PrepareTempFilesTask : CopyFilesTask() {
    open class FilesTaskParam internal constructor(i: Intent, private val _context: Context) :
        CopyFilesTaskParam(i) {
        override fun loadRecords(i: Intent): SrcDstCollection? {
            val paths = ArrayList<Path>()
            val loc = LocationsManager.getLocationsManager(_context).getFromIntent(i, paths)
            val wd = UserSettings.getSettings(_context).workDir
            val cols = ArrayList<SrcDstCollection>()
            try {
                for (srcPath in paths) {
                    val srcLoc = loc.copy()
                    srcLoc.currentPath = srcPath
                    var parentPath: Path? = null
                    try {
                        parentPath = srcPath.parentPath
                    } catch (ignored: IOException) {
                    }
                    if (parentPath == null) parentPath = srcPath
                    val sds = SrcDstSingle(srcLoc, getTmpLocation(loc, parentPath, _context, wd))
                    val sd: SrcDstCollection = if (srcPath.isFile) sds else SrcDstRec(sds)
                    cols.add(sd)
                }
                return SrcDstGroup(cols)
            } catch (e: IOException) {
                Logger.showAndLog(_context, e)
            }
            return null
        }
    }

    @Throws(Throwable::class)
    override fun doWork(context: Context, i: Intent): Any {
        val settings: Settings = UserSettings.getSettings(context)
        _fileSizeLimit = (1024 * 1024 * settings.maxTempFileSize).toLong()
        _wipe = settings.wipeTempFiles()
        super.doWork(context, i)
        return _tempFilesList
    }

    private var _fileSizeLimit: Long = 0
    private var _wipe = false
    private val _tempFilesList: MutableList<Location> = ArrayList()

    override fun initParam(i: Intent): FilesTaskParam {
        return FilesTaskParam(i, _context)
    }

    override val notificationMainTextId: Int
        get() = R.string.preparing_file

    @Throws(IOException::class)
    override fun copyFile(record: SrcDst): Boolean {
        val srcLoc = record.srcLocation.copy()
        var dstLoc: Location? =
            record.dstLocation ?: throw IOException("Failed to calc destination folder")
        dstLoc = dstLoc.copy()
        var tmpPath = calcDstPath(srcLoc.currentPath.file, dstLoc.currentPath.directory)
        val srcFolderLocation = srcLoc.copy()
        srcFolderLocation.currentPath = srcLoc.currentPath.parentPath
        if (tmpPath != null) {
            dstLoc.currentPath = tmpPath
            if (dstLoc.currentPath.exists()) {
                val monitor = getMonitor(_context!!)
                if (!monitor.isUpdateRequired(srcLoc, dstLoc)) {
                    incProcessedSize(srcLoc.currentPath.file.size.toInt())
                    _tempFilesList.add(dstLoc)
                    return true
                }
                getMonitor(_context!!).removeFileFromMonitor(dstLoc)
                deleteRecWithWiping(dstLoc.currentPath, _wipe)
            }
        } else {
            tmpPath = dstLoc.currentPath.directory.createFile(srcLoc.currentPath.file.name).path
            dstLoc.currentPath = tmpPath
        }
        if (super.copyFile(record)) {
            addFileToMonitor(srcLoc, srcFolderLocation, dstLoc)
            _tempFilesList.add(dstLoc)
            return true
        }
        return false
    }

    @Throws(IOException::class)
    override fun calcDstPath(src: FSRecord, dstFolder: Directory): Path? {
        var res = super.calcDstPath(src, dstFolder)
        if (res == null) res =
            if (src is File) dstFolder.createFile(src.name).path else if (src is Directory) dstFolder.createDirectory(
                src.name
            ).path else null

        return res
    }

    @Throws(IOException::class)
    override fun copyFile(srcFile: File, targetFolder: Directory): Boolean {
        val dstPath = calcDstPath(srcFile, targetFolder)
        if (dstPath != null && dstPath.isFile) {
            val dstFile = dstPath.file
            if (dstFile.size == srcFile.size &&
                dstFile.lastModified.time >= srcFile.lastModified.time
            ) {
                incProcessedSize(srcFile.size.toInt())
                return true
            }
        }
        if (srcFile.size > _fileSizeLimit) throw IOException(
            _context!!.getText(R.string.err_temp_file_is_too_big).toString()
        )
        return super.copyFile(srcFile, targetFolder)
    }

    @Throws(IOException::class)
    private fun addFileToMonitor(
        srcLocation: Location,
        srcFodlerLocation: Location,
        dstFilePath: Location
    ) {
        getMonitor(_context!!).addFileToMonitor(srcLocation, srcFodlerLocation, dstFilePath, false)
    }
}