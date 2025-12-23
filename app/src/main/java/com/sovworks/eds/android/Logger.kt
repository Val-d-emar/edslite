package com.sovworks.eds.android

import android.content.Context
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.settings.GlobalConfig
import java.io.IOException
import java.lang.Thread.UncaughtExceptionHandler

@Suppress("deprecation")
class Logger : UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, ex: Throwable) {
        if (!isLogDisabled) Log.e(TAG, "Uncaught main thread exception", ex)
        thread.threadGroup.destroy()
    }

    private fun showAndLogError(context: Context?, err: Throwable) {
        try {
            Log.w(TAG, err)
            if (context != null && Thread.currentThread() == Looper.getMainLooper().thread) showErrorMessage(
                context,
                err
            )
        } catch (ignored: Throwable) {
        }
    }

    companion object {
        const val TAG: String = "EDS"

        fun disableLog(`val`: Boolean) {
            isLogDisabled = `val`
        }

        private var l: Logger? = null
        var isLogDisabled: Boolean = false
            private set
        private val _syncObject = Any()

        @Throws(IOException::class)
        fun initLogger() {
            if (isLogDisabled) return
            l = Logger()
            val t = Looper.getMainLooper().thread
            t.uncaughtExceptionHandler = l
            Thread.setDefaultUncaughtExceptionHandler(l)
        }

        fun closeLogger() {
            val t = Looper.getMainLooper().thread
            t.uncaughtExceptionHandler = null
            Thread.setDefaultUncaughtExceptionHandler(null)
            l = null
        }

        @JvmStatic
        fun showAndLog(context: Context?, err: Throwable) {
            synchronized(_syncObject) {
                if (l != null) l!!.showAndLogError(context, err)
            }
        }

        @JvmStatic
        fun log(message: String) {
            if (isLogDisabled) return
            Log.i(TAG, message)
        }

        @JvmStatic
        fun log(e: Throwable?) {
            if (isLogDisabled) return
            Log.e(TAG, "Error", e)
        }

        @JvmStatic
        fun debug(message: String) {
            if (isLogDisabled) return
            if (GlobalConfig.isDebug()) Log.d(TAG, message)
        }

        fun getExceptionMessage(context: Context, err: Throwable): String {
            if (err is UserException) err.setContext(context)
            var msg = err.localizedMessage
            if (msg == null) {
                msg = err.message
                if (msg == null) msg = context.getString(R.string.generic_error_message)
            }
            return msg
        }


        fun showErrorMessage(context: Context, err: Throwable) {
            val errm = if (err is UserException) getExceptionMessage(
                context,
                err
            )
            else if (err.cause is UserException) getExceptionMessage(
                context,
                err.cause
            )
            else context.getString(R.string.generic_error_message)
            Toast.makeText(context, errm, Toast.LENGTH_LONG).show()
        }
    }
}
