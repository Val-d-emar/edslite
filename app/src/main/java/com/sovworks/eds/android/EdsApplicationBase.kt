package com.sovworks.eds.android

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION
import android.os.SystemClock
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.multidex.MultiDexApplication
import com.sovworks.eds.android.helpers.ExtendedFileInfoLoader.Companion.closeInstance
import com.sovworks.eds.android.providers.MainContentProvider
import com.sovworks.eds.android.providers.MainContentProviderBase.Companion.hasSelectionInClipboard
import com.sovworks.eds.android.settings.SystemConfig
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.locations.LocationsManager
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Timer
import java.util.TimerTask
import java.util.regex.Pattern

open class EdsApplicationBase : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        PRNGFixes.apply()

        com.sovworks.eds.settings.SystemConfig.setInstance(
            SystemConfig(
                applicationContext
            )
        )

        val us: UserSettings
        try {
            us = UserSettings.getSettings(applicationContext)
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(this, Logger.Companion.getExceptionMessage(this, e), Toast.LENGTH_LONG)
                .show()
            return
        }
        init(us)
        Logger.Companion.debug("Android sdk version is " + VERSION.SDK_INT)
    }

    protected fun init(settings: UserSettings) {
        try {
            if (settings.disableDebugLog()) Logger.Companion.disableLog(true)
            else Logger.Companion.initLogger()
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(this, Logger.Companion.getExceptionMessage(this, e), Toast.LENGTH_LONG)
                .show()
        }
    }

    companion object {
        const val BROADCAST_EXIT: String = "com.sovworks.eds.android.BROADCAST_EXIT"

        fun stopProgramBase(context: Context, removeNotifications: Boolean) {
            LocalBroadcastManager.getInstance(context).sendBroadcastSync(Intent(BROADCAST_EXIT))
            if (removeNotifications) (context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
            masterPassword = null
            LocationsManager.setGlobalLocationsManager(null)
            UserSettings.closeSettings()
            try {
                closeInstance()
            } catch (e: Throwable) {
                Logger.Companion.log(e)
            }

            try {
                val cm = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                if (MainContentProvider.hasSelectionInClipboard(cm)) cm.setPrimaryClip(
                    ClipData.newPlainText(
                        "Empty",
                        ""
                    )
                )
            } catch (e: Throwable) {
                Logger.Companion.log(e)
            }
        }

        fun exitProcess() {
            val t = Timer()
            t.schedule(object : TimerTask() {
                override fun run() {
                    try {
                        System.exit(0)
                    } catch (e: Throwable) {
                        Logger.Companion.log(e)
                    }
                }
            }, 4000)
        }

        @get:Synchronized
        @set:Synchronized
        var masterPassword: SecureBuffer?
            get() = _masterPass
            set(pass) {
                if (_masterPass != null) {
                    _masterPass!!.close()
                    _masterPass = null
                }
                _masterPass = pass
            }

        @Synchronized
        fun clearMasterPassword() {
            if (_masterPass != null) {
                _masterPass!!.close()
                _masterPass = null
            }
        }

        @Synchronized
        fun getMimeTypesMap(context: Context): Map<String, String?> {
            if (_mimeTypes == null) {
                try {
                    _mimeTypes = loadMimeTypes(context)
                } catch (e: IOException) {
                    throw RuntimeException("Failed loading mime types database", e)
                }
            }
            return _mimeTypes!!
        }

        @Synchronized
        fun updateLastActivityTime() {
            lastActivityTime = SystemClock.elapsedRealtime()
        }

        private var _masterPass: SecureBuffer? = null
        private var _mimeTypes: Map<String, String?>? = null

        @get:Synchronized
        var lastActivityTime: Long = 0
            private set

        private const val MIME_TYPES_PATH = "mime.types"

        @Throws(IOException::class)
        private fun loadMimeTypes(context: Context): Map<String, String?> {
            val p = Pattern.compile("^([^\\s/]+/[^\\s/]+)\\s+(.+)$")
            val reader = BufferedReader(
                InputStreamReader(
                    context.resources.assets.open(MIME_TYPES_PATH)
                )
            )
            try {
                val map = HashMap<String, String?>()
                var line: String?
                while ((reader.readLine().also { line = it }) != null) {
                    val m = p.matcher(line)
                    if (m.matches()) {
                        val mimeType = m.group(1)
                        val extsString = m.group(2)
                        val exts =
                            extsString!!.split("\\s".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        for (s in exts) map[s] = mimeType
                    }
                }
                return map
            } finally {
                reader.close()
            }
        }
    }
}
