package com.sovworks.eds.android.service

import com.sovworks.eds.android.R
import com.sovworks.eds.android.helpers.ExtendedFileInfoLoader.Companion.instance
import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.util.FilesCountAndSize
import com.sovworks.eds.fs.util.FilesOperationStatus
import com.sovworks.eds.fs.util.SrcDstCollection
import com.sovworks.eds.fs.util.SrcDstCollection.SrcDst
import java.io.IOException
import java.util.concurrent.CancellationException

internal open class DeleteFilesTask : FileOperationTaskBase() {
    override fun onCompleted(result: Result) {
        try {
            result.result
        } catch (ignored: CancellationException) {
        } catch (e: Throwable) {
            reportError(e)
        } finally {
            super.onCompleted(result)
        }
    }

    override fun initStatus(records: SrcDstCollection): FilesOperationStatus {
        val status = FilesOperationStatus()
        status.total = FilesCountAndSize.getFilesCount(records)
        return status
    }

    @Throws(Exception::class)
    override fun processRecord(record: SrcDst): Boolean {
        val srcPath = record.srcLocation.currentPath
        try {
            if (srcPath.isFile) {
                deleteFile(srcPath.file)
                instance.discardCache(record.srcLocation, srcPath)
            } else if (srcPath.isDirectory) deleteDir(srcPath.directory)
        } catch (e: IOException) {
            setError(IOException(String.format("Unable to delete record: %s", srcPath.pathDesc), e))
        }
        if (_currentStatus!!.processed.filesCount < _currentStatus!!.total.filesCount - 1) _currentStatus!!.processed.filesCount++
        updateUIOnTime()
        return true
    }

    @Throws(IOException::class)
    protected open fun deleteFile(file: File) {
        _currentStatus!!.fileName = file.name
        updateUIOnTime()
        file.delete()
    }

    @Throws(IOException::class)
    private fun deleteDir(dir: Directory) {
        _currentStatus!!.fileName = dir.name
        updateUIOnTime()
        dir.delete()
    }

    override fun getErrorMessage(ex: Throwable?): String {
        return _context!!.getString(R.string.delete_failed)
    }

    override val notificationMainTextId: Int
        get() = R.string.deleting_files
}