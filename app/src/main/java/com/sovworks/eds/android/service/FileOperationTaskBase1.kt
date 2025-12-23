package com.sovworks.eds.android.service

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat.Builder
import com.sovworks.eds.android.R
import com.sovworks.eds.fs.util.FilesOperationStatus
import com.sovworks.eds.fs.util.SrcDstCollection
import com.sovworks.eds.fs.util.SrcDstCollection.SrcDst
import java.util.concurrent.CancellationException


abstract class FileOperationTaskBase : ServiceTaskWithNotificationBase() {
    open class FileOperationParam
    internal constructor(protected val intent: Intent) {
        val records: SrcDstCollection?
            get() {
                if (_records == null) _records = loadRecords(intent)
                return _records
            }

        protected open fun loadRecords(i: Intent): SrcDstCollection? {
            return i.getParcelableExtra(FileOpsService.ARG_RECORDS)
        }

        private var _records: SrcDstCollection? = null
    }

    @Throws(Throwable::class)
    override fun doWork(context: Context, i: Intent): Any? {
        super.doWork(context, i)
        param = initParam(i)
        updateUIOnTime()
        _currentStatus = initStatus(param!!.records!!)
        processSrcDstCollection(param!!.records!!)
        if (_error != null) throw _error
        return null
    }

    override fun onCompleted(result: Result) {
        super.onCompleted(result)
        broadcastCompleted()
    }

    var _currentStatus: FilesOperationStatus? = null

    protected abstract fun initStatus(records: SrcDstCollection): FilesOperationStatus

    @Throws(Exception::class)
    protected abstract fun processRecord(record: SrcDst): Boolean

    protected open fun initParam(i: Intent): FileOperationParam {
        return FileOperationParam(i)
    }

    override fun updateUI() {
        if (_currentStatus != null) updateNotificationProgress()
        super.updateUI()
    }

    protected open val notificationMainTextId: Int
        get() = R.string.copying_files

    private fun updateNotificationProgress() {
        _notificationBuilder!!.setProgress(100, progress, false)
        _notificationBuilder!!.setContentText(notificationText)
    }

    protected open val notificationText: String?
        get() {
            val fn = _currentStatus!!.fileName
            return _context!!.getString(
                R.string.processing_file, fn ?: "",
                _currentStatus!!.processed.filesCount + 1,
                _currentStatus!!.total.filesCount
            )
        }

    protected val progress: Int
        get() {
            if (_currentStatus!!.total.totalSize == 0L) {
                if (_currentStatus!!.total.filesCount != 0) return ((_currentStatus!!.processed.filesCount / _currentStatus!!.total.filesCount.toFloat()) * 100).toInt()
            } else return ((_currentStatus!!.processed.totalSize / _currentStatus!!.total.totalSize.toFloat()) * 100).toInt()
            return 0
        }

    override fun initNotification(): Builder {
        return super.initNotification().setContentText(_context!!.getText(notificationMainTextId))
            .setProgress(100, 0, false)
    }

    @Throws(Exception::class)
    protected open fun processSrcDstCollection(col: SrcDstCollection) {
        for (rec in col) {
            if (isCancelled) throw CancellationException()
            if (!processRecord(rec)) break
        }
    }

    fun setError(err: Throwable?) {
        if (_error == null || err == null) _error = err
    }

    fun incProcessedSize(inc: Int) {
        //int prevPrc = (int) ((_currentStatus.processed.totalSize / (float) _currentStatus.total.totalSize) * 100);
        _currentStatus!!.processed.totalSize += inc.toLong()
        //int newPrc = (int) ((_currentStatus.processed.totalSize / (float) _currentStatus.total.totalSize) * 100);
        //if (prevPrc != newPrc)
        updateUIOnTime()
    }

    private fun broadcastCompleted() {
        _context!!.sendBroadcast(Intent(FileOpsService.BROADCAST_FILE_OPERATION_COMPLETED))
    }

    protected open var param: FileOperationParam? = null
        private set
    private var _error: Throwable? = null
}
