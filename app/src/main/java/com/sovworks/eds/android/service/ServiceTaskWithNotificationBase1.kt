package com.sovworks.eds.android.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.SystemClock
import androidx.core.app.NotificationCompat.Builder
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.activities.CancelTaskActivity.Companion.getCancelTaskIntent
import com.sovworks.eds.android.helpers.CompatHelper
import com.sovworks.eds.android.helpers.CompatHelperBase.Companion.getFileOperationsNotificationsChannelId

abstract class ServiceTaskWithNotificationBase internal constructor() : Task {
    @Throws(Throwable::class)
    override fun doWork(context: Context, i: Intent): Any? {
        initTask(context, i)
        return null
    }

    override fun onCompleted(result: Result) {
        removeNotification()
    }

    override fun cancel() {
        isCancelled = true
    }

    var isCancelled: Boolean = false
        private set
    protected var _context: Context? = null
    var _notificationBuilder: Builder? = null
    private var _prevUpdateTime: Long = 0
    private var _taskId = 0

    @Throws(Exception::class)
    protected fun initTask(context: Context?, i: Intent?) {
        _context = context
        _taskId = FileOpsService.getNewNotificationId()
        _notificationBuilder = initNotification()
    }

    protected open fun updateUI() {
        updateNotification()
    }

    protected open fun getErrorMessage(ex: Throwable?): String {
        return Logger.getExceptionMessage(_context, ex)
    }

    private fun getErrorDetails(ex: Throwable): String {
        var msg = ex.localizedMessage
        if (msg == null) {
            msg = ex.message
            if (msg == null) msg = ""
        }
        return msg
    }

    fun reportError(err: Throwable) {
        Logger.log(err)
        showNotificationMessage(getErrorMessage(err), getErrorDetails(err))
    }

    private fun showNotificationMessage(title: String?, message: String) {
        if (title == null) return
        val nb: Builder =
            Builder(_context, CompatHelper.getFileOperationsNotificationsChannelId(_context))
                .setSmallIcon(if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) R.drawable.ic_notification_new else R.drawable.ic_notification)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(message)
        //Gingerbread compatibility
        val emptyIntent = Intent()
        val pi =
            PendingIntent.getActivity(_context, 0, emptyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        nb.setContentIntent(pi)

        val nm = _context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm?.notify(FileOpsService.getNewNotificationId(), nb.build())
    }

    protected open fun initNotification(): Builder {
        val nb: Builder =
            Builder(_context, CompatHelper.getFileOperationsNotificationsChannelId(_context))
                .setContentTitle(_context!!.getString(R.string.eds))
                .setSmallIcon(if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) R.drawable.ic_notification_new else R.drawable.ic_notification)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(
                    R.drawable.ic_action_cancel,
                    _context!!.getString(android.R.string.cancel),
                    FileOpsService.getCancelTaskActionPendingIntent(_context, _taskId)
                )
        if (VERSION.SDK_INT < VERSION_CODES.JELLY_BEAN) nb.setContentIntent(
            PendingIntent.getActivity(
                _context,
                _taskId,
                getCancelTaskIntent(_context, _taskId),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        return nb
    }

    fun removeNotification() {
        val nm = _context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm?.cancel(_taskId)
    }

    private fun updateNotification() {
        val nm = _context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm?.notify(_taskId, _notificationBuilder!!.build())
    }

    fun updateUIOnTime() {
        val time = SystemClock.uptimeMillis()
        if (time - _prevUpdateTime > 1000) {
            updateUI()
            _prevUpdateTime = time
        }
    }
}
