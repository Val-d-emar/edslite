package com.sovworks.eds.android.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat.Builder
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.helpers.CompatHelper
import com.sovworks.eds.android.helpers.CompatHelperBase.Companion.getServiceRunningNotificationsChannelId
import com.sovworks.eds.android.helpers.TempFilesMonitor.Companion.getMonitor
import com.sovworks.eds.android.locations.activities.CloseLocationsActivity
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.util.Util
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.settings.Settings
import java.io.IOException

open class LocationsServiceBase : Service() {
    class InactivityCheckReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val lm = LocationsManager.getLocationsManager(context, false) ?: return
                val uri = intent.getParcelableExtra<Uri>(LocationsManager.PARAM_LOCATION_URI)
                if (uri != null) {
                    val loc = lm.getLocation(uri) as EDSLocation
                    closeIfInactive(context, loc)
                }
            } catch (e: Throwable) {
                Logger.log(e)
            }
        }

        private fun closeIfInactive(context: Context, loc: EDSLocation) {
            val tm = loc.externalSettings.autoCloseTimeout
            Logger.debug("Checking if " + loc.title + " container is inactive")
            if (tm <= 0) return
            val ct = SystemClock.elapsedRealtime()
            Logger.debug("Current time = $ct")
            if (loc.isOpenOrMounted) {
                val lastActivityTime = loc.lastActivityTime
                Logger.debug("Container " + loc.title + " is open. Last activity time is " + lastActivityTime)
                if (ct - lastActivityTime > tm) {
                    Logger.debug("Starting close container task for " + loc.title + " after inactivity timeout.")
                    FileOpsService.closeContainer(context, loc)
                    return
                }
            }
            registerInactiveContainerCheck(context, loc)
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            _locationsManager = LocationsManager.getLocationsManager(this, true)
            _settings = UserSettings.getSettings(this)
            _shutdownReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    Logger.debug("Device shutdown. Closing locations")
                    _locationsManager.closeAllLocations(true, false)
                }
            }
            registerReceiver(_shutdownReceiver, IntentFilter(Intent.ACTION_SHUTDOWN))
            registerReceiver(
                _shutdownReceiver,
                IntentFilter("android.intent.action.QUICKBOOT_POWEROFF")
            )
            _inactivityCheckReceiver = InactivityCheckReceiver()
            registerReceiver(_inactivityCheckReceiver, IntentFilter(ACTION_CHECK_INACTIVE_LOCATION))
        } catch (e: Exception) {
            Logger.showAndLog(this, e)
        }
    }


    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (hasOpenLocations()) {
            startForeground(
                NOTIFICATION_RUNNING_SERVICE,
                serviceRunningNotification
            )
            getMonitor(this).startChangesMonitor()
            return START_STICKY
        }
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Logger.debug("LocationsService onDestroy")
        stopForeground(true)
        if (_shutdownReceiver != null) {
            unregisterReceiver(_shutdownReceiver)
            _shutdownReceiver = null
        }
        if (_inactivityCheckReceiver != null) {
            unregisterReceiver(_inactivityCheckReceiver)
            _inactivityCheckReceiver = null
        }
        getMonitor(this).stopChangesMonitor()
        _locationsManager!!.closeAllLocations(true, true)
        deleteMirror()
        _settings = null
        _locationsManager = null
        super.onDestroy()
    }

    protected var _locationsManager: LocationsManager? = null
    protected var _settings: Settings? = null
    protected var _shutdownReceiver: BroadcastReceiver? = null
    protected var _inactivityCheckReceiver: BroadcastReceiver? = null

    private fun deleteMirror() {
        try {
            val l = FileOpsService.getSecTempFolderLocation(
                _settings!!.workDir, this
            )
            if (l != null) Util.deleteFiles(l.currentPath)
        } catch (e: IOException) {
            Logger.showAndLog(this, e)
        }
    }

    protected fun hasOpenLocations(): Boolean {
        val lm = LocationsManager.getLocationsManager(this)
        return lm.hasOpenLocations()
    }

    private val serviceRunningNotification: Notification
        get() {
            val i = Intent(this, FileManagerActivity::class.java)
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val builder: Builder = Builder(
                this,
                CompatHelper.getServiceRunningNotificationsChannelId(this)
            )
                .setContentTitle(getString(R.string.eds_service_is_running))
                .setSmallIcon(if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) R.drawable.ic_notification_new else R.drawable.ic_notification)
                .setContentText("")
                .setContentIntent(PendingIntent.getActivity(this, 0, i, 0))
                .setOngoing(true)
                .addAction(
                    R.drawable.ic_action_cancel,
                    getString(R.string.close_all_containers),
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, CloseLocationsActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            val n = builder.build()
            n.flags =
                n.flags or (Notification.FLAG_NO_CLEAR or Notification.FLAG_FOREGROUND_SERVICE)
            return n
        }

    companion object {
        const val NOTIFICATION_RUNNING_SERVICE: Int = 1

        fun startService(context: Context) {
            context.startService(Intent(context, LocationsService::class.java))
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, LocationsService::class.java))
        }

        const val ACTION_CHECK_INACTIVE_LOCATION: String =
            "com.sovworks.eds.android.CHECK_INACTIVE_LOCATION"

        @JvmStatic
		fun registerInactiveContainerCheck(context: Context, loc: EDSLocation) {
            var triggerTime = loc.externalSettings.autoCloseTimeout.toLong()
            if (triggerTime == 0L) return
            triggerTime += SystemClock.elapsedRealtime()
            val i = Intent(ACTION_CHECK_INACTIVE_LOCATION)
            i.putExtra(LocationsManager.PARAM_LOCATION_URI, loc.locationUri)
            val pi = PendingIntent.getBroadcast(
                context,
                loc.id.hashCode(),
                i,
                PendingIntent.FLAG_ONE_SHOT
            )
            LocationsService.setCheckTimer(context, pi, triggerTime)
        }

        protected fun setCheckTimer(context: Context, pi: PendingIntent, triggerTime: Long) {
            val am = context.getSystemService(ALARM_SERVICE) as AlarmManager
            am[AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime] = pi
        }
    }
}
