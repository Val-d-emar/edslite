package com.sovworks.eds.android.service

import android.content.Intent
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.activities.FileManagerActivityBase.Companion.getOverwriteRequestIntent
import com.sovworks.eds.android.fs.DocumentTreeFS
import com.sovworks.eds.android.helpers.ExtendedFileInfoLoader.Companion.instance
import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.FSRecord
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.errors.NoFreeSpaceLeftException
import com.sovworks.eds.fs.util.FilesCountAndSize
import com.sovworks.eds.fs.util.FilesOperationStatus
import com.sovworks.eds.fs.util.SrcDstCollection
import com.sovworks.eds.fs.util.SrcDstCollection.SrcDst
import com.sovworks.eds.fs.util.SrcDstPlain
import org.json.JSONException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException

internal open class CopyFilesTask : FileOperationTaskBase() {
    open class CopyFilesTaskParam internal constructor(i: Intent) : FileOperationParam(i) {
        open fun forceOverwrite(): Boolean {
            return _overwrite
        }

        private val _overwrite =
            i.getBooleanExtra(FileOpsService.ARG_OVERWRITE, false)
        val overwriteTargetsStorage: SrcDstPlain = SrcDstPlain()
    }

    override fun onCompleted(result: Result) {
        try {
            result.result
            val p: CopyFilesTaskParam? = param
            if (p!!.overwriteTargetsStorage != null && !p.overwriteTargetsStorage.isEmpty()) _context!!.startActivity(
                getOverwriteRequestIntent(
                    p.overwriteTargetsStorage
                )
            )
        } catch (ignored: CancellationException) {
        } catch (e: Throwable) {
            reportError(e)
        } finally {
            super.onCompleted(result)
        }
    }

    override fun initParam(i: Intent): CopyFilesTaskParam {
        return CopyFilesTaskParam(i)
    }

    override fun initStatus(records: SrcDstCollection): FilesOperationStatus {
        val status = FilesOperationStatus()
        status.total = FilesCountAndSize.getFilesCountAndSize(false, records)
        return status
    }

    override val notificationText: String?
        get() {
            var txt = super.getNotificationText()
            val curTime = System.currentTimeMillis()
            val prevTime = _currentStatus!!.prevUpdateTime
            if (curTime - prevTime > 1000) {
                if (prevTime > 0) {
                    val speed =
                        1000 * (_currentStatus!!.processed.totalSize - _currentStatus!!.prevProcSize).toFloat() / (1024 * 1024 * (curTime - prevTime))
                    txt += ", " + _context!!.getString(R.string.speed, speed)
                }
                _currentStatus!!.prevProcSize = _currentStatus!!.processed.totalSize
                _currentStatus!!.prevUpdateTime = curTime
            }
            return txt
        }

    @Throws(Exception::class)
    override fun processRecord(record: SrcDst): Boolean {
        try {
            copyFiles(record)
        } catch (e: NoFreeSpaceLeftException) {
            throw com.sovworks.eds.android.errors.NoFreeSpaceLeftException(_context)
        } catch (e: IOException) {
            setError(e)
        }
        return true
    }

    @Throws(IOException::class)
    protected fun copyFiles(record: SrcDst): Boolean {
        var res = true
        val src = record.srcLocation.currentPath
        if (src.isFile) {
            if (!copyFile(record)) res = false
            if (_currentStatus!!.processed.filesCount < _currentStatus!!.total.filesCount - 1) _currentStatus!!.processed.filesCount++
        } else if (!makeDir(record)) res = false

        updateUIOnTime()
        return res
    }

    @Throws(IOException::class)
    private fun makeDir(record: SrcDst): Boolean {
        val src = record.srcLocation.currentPath
        val dstLocation = record.dstLocation
            ?: throw IOException("Failed to determine destination folder for " + src.pathDesc)
        return makeDir(src, dstLocation.currentPath)
    }

    @Throws(IOException::class)
    private fun makeDir(srcPath: Path, dstPath: Path): Boolean {
        return makeDir(srcPath.directory, dstPath.directory)
    }

    @Throws(IOException::class)
    private fun makeDir(srcFolder: Directory, targetFolder: Directory): Boolean {
        val srcName = srcFolder.name
        _currentStatus!!.fileName = srcName
        updateUIOnTime()
        val dstPath = calcDstPath(srcFolder, targetFolder)
        if (dstPath == null || !dstPath.exists()) {
            targetFolder.createDirectory(srcName)
            return true
        }
        return false
    }

    @Throws(IOException::class)
    protected open fun copyFile(record: SrcDst): Boolean {
        val src = record.srcLocation.currentPath
        val dstLocation = record.dstLocation
            ?: throw IOException("Failed to determine destination folder for " + src.pathDesc)
        val dst = dstLocation.currentPath
        if (copyFile(src, dst)) {
            instance.discardCache(record.dstLocation, dst)
            return true
        } else {
            param.overwriteTargetsStorage.add(record)
            return false
        }
    }

    @Throws(IOException::class)
    protected fun copyFile(srcPath: Path, dstPath: Path): Boolean {
        return copyFile(srcPath.file, dstPath.directory)
    }


    @Throws(IOException::class)
    protected open fun calcDstPath(src: FSRecord, dstFolder: Directory): Path? {
        return calcPath(dstFolder, src.name)
    }

    fun calcPath(dstFolder: Directory, name: String?): Path? {
        return try {
            dstFolder.path.combine(name)
        } catch (ignored: IOException) {
            null
        }
    }

    @Throws(IOException::class)
    protected open fun copyFile(srcFile: File, targetFolder: Directory): Boolean {
        val srcName = srcFile.name
        _currentStatus!!.fileName = srcName
        updateUIOnTime()
        var dstPath = calcDstPath(srcFile, targetFolder)
        if (dstPath != null && !dstPath.exists()) dstPath = null
        if (!param.forceOverwrite() && dstPath != null) return false

        if (targetFolder !is DocumentTreeFS.Directory) {
            val size = srcFile.size
            val space = targetFolder.freeSpace
            if (space > 0 && size > space) throw NoFreeSpaceLeftException()
        }
        return copyFile(
            srcFile,
            if (dstPath != null) dstPath.file else targetFolder.createFile(srcName)
        )
    }


    @Throws(IOException::class)
    protected open fun copyFile(srcFile: File, dstFile: File): Boolean {
        val srcDate = srcFile.lastModified
        var fin: InputStream? = null
        var fout: OutputStream? = null
        val buffer = ByteArray(8 * 1024)
        var bytesRead: Int
        try {
            fin = srcFile.inputStream
            fout = dstFile.outputStream
            while ((fin.read(buffer).also { bytesRead = it }) >= 0) {
                if (isCancelled) throw CancellationException()
                fout.write(buffer, 0, bytesRead)
                incProcessedSize(bytesRead)
            }
        } finally {
            fin?.close()
            fout?.close()
        }
        try {
            dstFile.lastModified = srcDate
        } catch (e: IOException) {
            Logger.log(e)
        }
        return true
    }

    override fun getErrorMessage(ex: Throwable?): String {
        return _context!!.getString(R.string.copy_failed)
    }

    @Throws(IOException::class, JSONException::class)
    protected open fun getOverwriteRequestIntent(filesToOverwrite: SrcDstCollection?): Intent {
        return FileManagerActivity.getOverwriteRequestIntent
        (_context, false, filesToOverwrite)
    }

    override var param: FileOperationParam?
        get() = super.getParam() as CopyFilesTaskParam
        set(param) {
            super.param = param
        }
}