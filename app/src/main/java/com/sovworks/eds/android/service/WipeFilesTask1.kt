package com.sovworks.eds.android.service

import android.content.Context
import android.content.Intent
import com.sovworks.eds.android.R
import com.sovworks.eds.android.helpers.TempFilesMonitor
import com.sovworks.eds.android.helpers.TempFilesMonitor.Companion.getMonitor
import com.sovworks.eds.android.helpers.WipeFilesTask.Companion.wipeFile
import com.sovworks.eds.android.helpers.WipeFilesTask.ITask
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.util.FilesCountAndSize
import com.sovworks.eds.fs.util.FilesOperationStatus
import com.sovworks.eds.fs.util.SrcDstCollection
import java.io.IOException
import java.util.concurrent.CancellationException

internal open class WipeFilesTask(private val _wipe: Boolean) : DeleteFilesTask() {
    private var _mon: TempFilesMonitor? = null

    @Throws(Throwable::class)
    override fun doWork(context: Context, i: Intent): Any? {
        _mon = getMonitor(context)
        return super.doWork(context, i)
    }

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

    override val notificationMainTextId: Int
        get() = R.string.wiping_files

    override fun initStatus(records: SrcDstCollection): FilesOperationStatus {
        val status = FilesOperationStatus()
        status.total = if (_wipe) FilesCountAndSize.getFilesCountAndSize(
            false,
            records
        ) else FilesCountAndSize.getFilesCount(records)
        return status
    }

    @Throws(IOException::class)
    override fun deleteFile(file: File) {
        synchronized(_mon!!.syncObject) {
            wipeFile(
                file,
                _wipe,
                object : ITask {
                    override fun progress(sizeInc: Int) {
                        incProcessedSize(sizeInc)
                    }

                    override fun cancel(): Boolean {
                        return isCancelled
                    }
                }
            )
        }
    }
}