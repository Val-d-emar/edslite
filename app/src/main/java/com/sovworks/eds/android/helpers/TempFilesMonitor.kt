package com.sovworks.eds.android.helpers

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.errors.ExternalStorageNotAvailableException
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.crypto.SimpleCrypto
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.PathUtil
import com.sovworks.eds.fs.util.SrcDstSingle
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.Openable
import java.io.IOException
import java.util.Date
import java.util.TreeMap
import kotlin.collections.Map.Entry

class TempFilesMonitor
private constructor(context: Context) {
    @Throws(IOException::class, UserException::class)
    fun startFile(fileLocation: Location) {
        if (!isTempDirWriteable) throw ExternalStorageNotAvailableException(_context)
        decryptAndStartFile(fileLocation)
    }

    @Throws(IOException::class)
    fun isUpdateRequired(srcLocation: Location, deviceLocation: Location): Boolean {
        synchronized(syncObject) {
            val deviceUri = deviceLocation.locationUri
            val ofi = _openedFiles[deviceUri]
            if (ofi != null) {
                val srcLastModified = srcLocation.currentPath.file.lastModified.time
                return ofi.srcLastModified < srcLastModified
            }
            return true
        }
    }

    @Throws(IOException::class)
    fun addFileToMonitor(
        srcLocation: Location,
        srcFolderLocation: Location?,
        devicePath: Location,
        isReadOnly: Boolean
    ): Boolean {
        synchronized(syncObject) {
            val ofi = OpenFileInfo()
            ofi.srcFileLocation = srcLocation
            ofi.srcFolderLocation = srcFolderLocation
            ofi.devicePath = devicePath
            ofi.srcLastModified = srcLocation.currentPath.file.lastModified.time
            ofi.isReadOnly = isReadOnly
            val f = devicePath.currentPath.file
            ofi.lastModified = f.lastModified.time
            ofi.prevSize = f.size
            _openedFiles.put(devicePath.locationUri, ofi)
        }
        return true
    }

    @Throws(IOException::class)
    fun updateMonitoredInfo(deviceLocation: Location, srclastModified: Date) {
        synchronized(syncObject) {
            val ofi = _openedFiles[deviceLocation.locationUri]
            if (ofi != null) ofi.srcLastModified = srclastModified.time
        }
    }

    fun removeFileFromMonitor(tmpPath: Location) {
        synchronized(syncObject) {
            _openedFiles.remove(tmpPath.locationUri)
        }
    }

    @Synchronized
    fun startChangesMonitor() {
        if (_modCheckTask == null) {
            _modCheckTask = ModificationCheckingTask()
            _modCheckTask!!.start()
        }
    }

    @Synchronized
    fun stopChangesMonitor() {
        if (_modCheckTask == null) return
        _modCheckTask!!.cancel()
        try {
            _modCheckTask!!.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        _modCheckTask = null
    }

    private inner class ModificationCheckingTask : Thread() {
        override fun run() {
            while (!_stop) {
                synchronized(this.syncObject) {
                    val iter: MutableIterator<Entry<Uri, OpenFileInfo>> =
                        _openedFiles.entries.iterator()
                    while (iter.hasNext()) {
                        val fileInfo = iter.next().value
                        try {
                            if (!fileInfo.devicePath!!.currentPath.exists() ||
                                (fileInfo.srcFolderLocation is Openable &&
                                        !(fileInfo.srcFolderLocation as Openable).isOpen
                                        )
                            ) iter.remove()
                            else if (!fileInfo.isReadOnly) {
                                //long lastModified = fileInfo.devicePath.getAbsoluteFile().lastModified(); //fileInfo.devicePath.lastModified();
                                val f = fileInfo.devicePath!!.currentPath.file
                                val lastModified = f.lastModified.time
                                val prevSize = f.size
                                if (fileInfo.lastModified != lastModified || fileInfo.prevSize != prevSize) {
                                    fileInfo.lastModified = lastModified
                                    fileInfo.prevSize = prevSize
                                    saveChangedFile(fileInfo.srcFolderLocation, fileInfo.devicePath)
                                }
                            }
                        } catch (e: Exception) {
                            Logger.log(e)
                        }
                    }
                }
                try {
                    sleep(Companion.POLLING_INTERVAL.toLong())
                } catch (ignored: InterruptedException) {
                }
            }
        }

        fun cancel() {
            _stop = true
        }

        private var _stop = false

        companion object {
            private const val POLLING_INTERVAL = 3000
        }
    }


    val syncObject: Any = Any()
    private val _openedFiles = TreeMap<Uri, OpenFileInfo>()
    private val _context: Context? = context
    private var _modCheckTask: ModificationCheckingTask? = null

    @Throws(IOException::class, UserException::class)
    private fun decryptAndStartFile(srcLocation: Location) {
        if (_context == null) return
        FileOpsService.startTempFile(_context, srcLocation)
    }

    @Throws(IOException::class, UserException::class)
    private fun saveChangedFile(srcLocation: Location?, tmpPath: Location?) {
        FileOpsService.saveChangedFile(_context, SrcDstSingle(tmpPath, srcLocation))
    }

    private val isTempDirWriteable: Boolean
        get() = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun deleteRecWithWiping(path: Path, wipe: Boolean) {
            if (!path.exists()) return
            if (path.isDirectory) {
                val dc = path.directory.list()
                try {
                    for (rec in dc) deleteRecWithWiping(rec, wipe)
                } finally {
                    dc.close()
                }
                path.directory.delete()
            } else if (path.isFile) {
                if (wipe) WipeFilesTask.Companion.wipeFileRnd(path.file, null)
                else path.file.delete()
            }
        }

        @Throws(IOException::class)
        fun getTmpLocation(location: Location, context: Context?, workDir: String?): Location {
            return getTmpLocation(location, location.currentPath, context, workDir)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun getTmpLocation(
            rootLocation: Location,
            path: Path,
            context: Context?,
            workDir: String?
        ): Location {
            return getTmpLocation(rootLocation, path, context, workDir, true)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun getTmpLocation(
            rootLocation: Location,
            path: Path,
            context: Context?,
            workDir: String?,
            monitored: Boolean
        ): Location {
            val loc = if (monitored) FileOpsService.getMonitoredMirrorLocation(
                workDir,
                context,
                rootLocation.id
            ) else FileOpsService.getNonMonitoredMirrorLocation(workDir, context, rootLocation.id)
            loc.currentPath = PathUtil.getDirectory(
                loc.currentPath,
                SimpleCrypto.calcStringMD5(path.pathString)
            ).path
            return loc
        }

        @JvmStatic
        @Synchronized
        fun getMonitor(context: Context): TempFilesMonitor {
            if (_instance == null) _instance = TempFilesMonitor(context)

            return _instance!!
        }

        private var _instance: TempFilesMonitor? = null
    }
}
