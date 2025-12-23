package com.sovworks.eds.android.service

import android.app.IntentService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.sovworks.eds.android.EdsApplication
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.errors.InputOutputException
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.android.providers.MainContentProvider
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.PathUtil
import com.sovworks.eds.fs.util.SrcDstCollection
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.locations.DeviceBasedLocation
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.settings.GlobalConfig
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.regex.Pattern

abstract class FileOpsServiceBase : IntentService(TAG) {
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (ACTION_CANCEL_TASK == intent.action) {
            if (_currentTask != null) {
                _currentTask!!.cancel()
                _taskCancelled = true
            }
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        _currentTask = null
    }

    override fun onHandleIntent(intent: Intent) {
        val notificationId = intent.getIntExtra(ARG_NOTIFICATION_ID, -1)
        if (notificationId >= 0) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }
        _taskCancelled = false
        val task = getTask(intent)

        if (task == null) Logger.log("Unsupported action: " + intent.action)
        else {
            _currentTask = task
            var result: Result? = null
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FileOpTask $intent"
            )
            wakeLock.acquire()
            try {
                if (GlobalConfig.isDebug()) {
                    val lbm = LocalBroadcastManager.getInstance(
                        applicationContext
                    )
                    val notifIntent = Intent(intent.action)
                    notifIntent.putExtra(ARG_ORIG_INTENT, intent)
                    lbm.sendBroadcast(notifIntent)
                }
                val res = _currentTask!!.doWork(this, intent)
                result = if (_taskCancelled) Result(
                    CancellationException(), true
                ) else Result(res)
            } catch (e: Throwable) {
                result = Result(e, false)
            } finally {
                try {
                    wakeLock.release()
                    _currentTask!!.onCompleted(result)
                    if (GlobalConfig.isDebug()) {
                        val lbm = LocalBroadcastManager.getInstance(
                            applicationContext
                        )
                        val notifIntent = Intent(intent.action)
                        notifIntent.putExtra(ARG_ORIG_INTENT, intent)
                        notifIntent.putExtra(ARG_TASK_COMPLETED, true)
                        lbm.sendBroadcast(notifIntent)
                    }
                } catch (e: Throwable) {
                    Logger.log(e)
                } finally {
                    _currentTask = null
                }
            }
        }
    }

    private var _currentTask: Task? = null
    protected var _taskCancelled: Boolean = false

    protected fun getTask(intent: Intent): Task? {
        when (intent.action) {
            ACTION_COPY -> return CopyFilesTask()
            ACTION_MOVE -> return MoveFilesTask()
            ACTION_RECEIVE -> return ReceiveFilesTask()
            ACTION_DELETE -> return DeleteFilesTask()
            ACTION_WIPE -> return WipeFilesTask(true)
            ACTION_CLEAR_TEMP_FOLDER -> return ClearTempFolderTask()
            ACTION_START_TEMP_FILE -> return StartTempFileTask()
            ACTION_SAVE_CHANGED_FILE -> return SaveTempFileChangesTask()
            ACTION_PREPARE_TEMP_FILE -> return PrepareTempFilesTask()
            ACTION_SEND_TASK -> return ActionSendTask()
            ACTION_CLOSE_CONTAINER -> return CloseContainerTask()
        }
        return null
    }

    companion object {
        const val INTENT_PARAM_TASK_ID: String = "TASK_ID"
        const val BROADCAST_FILE_OPERATION_COMPLETED: String =
            "com.sovworks.eds.android.FILE_OPERATION_COMPLETED"
        const val ARG_NOTIFICATION_ID: String = "com.sovworks.eds.NOTIFICATION_ID"

        const val ARG_TASK_COMPLETED: String = "com.sovworks.eds.android.TASK_COMPLETED"
        const val ARG_ORIG_INTENT: String = "com.sovworks.eds.android.ORIG_INTENT"

        @Throws(IOException::class)
        fun getSecTempFolderLocation(workDir: String?, context: Context): Location {
            var res: Location? = null
            if (workDir != null && !workDir.isEmpty()) {
                try {
                    res = LocationsManager.getLocationsManager(context)
                        .getLocation(Uri.parse(workDir))
                    if (!res.currentPath.isDirectory) res = null
                } catch (e: Exception) {
                    Logger.log(e)
                    res = null
                }
            }
            if (res == null) {
                var extDir = context.getExternalFilesDir(null)
                if (extDir == null) extDir = context.filesDir
                if (extDir == null) extDir = context.cacheDir
                res = DeviceBasedLocation(
                    UserSettings.getSettings(context),
                    extDir!!.absolutePath
                )
            }
            res.currentPath = PathUtil.getDirectory(res.currentPath, "temp").path

            return res
        }

        @Throws(IOException::class)
        fun getMirrorLocation(
            workDir: String?, context: Context,
            locationId: String?
        ): Location {
            val secTempLocation = getSecTempFolderLocation(workDir, context)
            secTempLocation.currentPath = PathUtil.getDirectory(
                secTempLocation.currentPath,
                "mirror",
                locationId
            ).path
            return secTempLocation
        }

        @Throws(IOException::class)
        fun getMonitoredMirrorLocation(
            workDir: String?, context: Context,
            locationId: String?
        ): Location {
            val mirrorLocation = getMirrorLocation(workDir, context, locationId)
            mirrorLocation.currentPath =
                PathUtil.getDirectory(mirrorLocation.currentPath, "mon").path
            return mirrorLocation
        }

        @Throws(IOException::class)
        fun getNonMonitoredMirrorLocation(
            workDir: String?, context: Context,
            locationId: String?
        ): Location {
            val mirrorLocation = getMirrorLocation(workDir, context, locationId)
            mirrorLocation.currentPath =
                PathUtil.getDirectory(mirrorLocation.currentPath, "nomon").path
            return mirrorLocation
        }

        @Throws(IOException::class)
        fun getMimeTypeFromExtension(context: Context, path: Path): String? {
            return getMimeTypeFromExtension(context, path.file)
        }

        @Throws(IOException::class)
        fun getMimeTypeFromExtension(context: Context, file: File): String? {
            return getMimeTypeFromExtension(context, StringPathUtil(file.name).fileExtension)
        }

        fun getMimeTypeFromExtension(context: Context, filenameExtension: String): String? {
            var filenameExtension = filenameExtension
            filenameExtension = filenameExtension.lowercase(
                context.resources.configuration.locale
            )
            val settings = UserSettings.getSettings(context)
            val custMimes = settings.extensionsMimeMapString
            if (custMimes.length > 0) {
                try {
                    val p = Pattern.compile(
                        "^\\s*$filenameExtension\\s+([^\\s]+)$",
                        Pattern.CASE_INSENSITIVE or Pattern.MULTILINE
                    )
                    val m = p.matcher(custMimes)
                    if (m.find()) return m.group(1)
                } catch (e: Exception) {
                    Logger.showAndLog(context, e)
                }
            }
            val mime: String = EdsApplication.getMimeTypesMap(context)
                .get(filenameExtension) //MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            return mime ?: "application/octet-stream"
        }

        fun getCancelTaskActionPendingIntent(context: Context?, taskId: Int): PendingIntent {
            val i = Intent(context, FileOpsService::class.java)
            i.setAction(ACTION_CANCEL_TASK)
            i.putExtra(INTENT_PARAM_TASK_ID, taskId)
            return PendingIntent.getService(context, taskId, i, PendingIntent.FLAG_ONE_SHOT)
        }

        @Throws(UserException::class)
        fun startFileViewer(context: Context, fileLocation: Location) {
            try {
                var uri = fileLocation.getDeviceAccessibleUri(fileLocation.currentPath)
                if (uri == null) uri = MainContentProvider.getContentUriFromLocation(fileLocation)
                FileOpsService.startFileViewer(
                    context,
                    uri,
                    getMimeTypeFromExtension(context, fileLocation.currentPath.file)
                )
            } catch (e: IOException) {
                throw InputOutputException(context, e)
            }
        }

        @Throws(UserException::class)
        fun startFileViewer(context: Context, uri: Uri?, mimeType: String?) {
            val intent = Intent(Intent.ACTION_VIEW) // ,Uri.fromFile(_targetFile.devicePath));
            if (mimeType != null && !mimeType.isEmpty()) intent.setDataAndType(uri, mimeType)
            else intent.setData(uri)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION // | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET
                // | Intent.FLAG_ACTIVITY_NO_HISTORY
                // | Intent.FLAG_ACTIVITY_CLEAR_TOP
            )

            //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            //	intent.setClipData(ClipData.newUri(context.getContentResolver(), "", uri));
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                throw UserException(
                    context, R.string.err_no_application_found,
                    e
                )
            }
        }

        @get:Synchronized
        val newNotificationId: Int
            get() = NOTIFICATION_COUNTER++

        fun copyFiles(context: Context, files: SrcDstCollection?, forceOverwrite: Boolean) {
            val i = Intent(context, FileOpsService::class.java)
            i.setAction(ACTION_COPY)
            i.putExtra(ARG_RECORDS, files)
            i.putExtra(ARG_OVERWRITE, forceOverwrite)
            context.startService(i)
        }

        fun moveFiles(context: Context, files: SrcDstCollection?, forceOverwrite: Boolean) {
            val i = Intent(context, FileOpsService::class.java)
            i.setAction(ACTION_MOVE)
            i.putExtra(ARG_RECORDS, files)
            i.putExtra(ARG_OVERWRITE, forceOverwrite)
            context.startService(i)
        }

        fun receiveFiles(context: Context, files: SrcDstCollection?, forceOverwrite: Boolean) {
            val i = Intent(context, FileOpsService::class.java)
            i.setAction(ACTION_RECEIVE)
            i.putExtra(ARG_RECORDS, files)
            i.putExtra(ARG_OVERWRITE, forceOverwrite)
            context.startService(i)
        }

        fun deleteFiles(context: Context, files: SrcDstCollection?) {
            val i = Intent(context, FileOpsService::class.java)
            i.setAction(ACTION_DELETE)
            i.putExtra(ARG_RECORDS, files)
            context.startService(i)
        }

        fun wipeFiles(context: Context, files: SrcDstCollection?) {
            val i = Intent(context, FileOpsService::class.java)
            i.setAction(ACTION_WIPE)
            i.putExtra(ARG_RECORDS, files)
            context.startService(i)
        }

        fun closeContainer(context: Context, container: EDSLocation) {
            val i = Intent(context, FileOpsService::class.java)
            i.setAction(ACTION_CLOSE_CONTAINER)
            LocationsManager.storePathsInIntent(i, container, null)
            context.startService(i)
        }

        fun clearTempFolder(context: Context, exitProgram: Boolean) {
            val i = Intent(context, FileOpsService::class.java)
            i.setAction(ACTION_CLEAR_TEMP_FOLDER)
            i.putExtra(ClearTempFolderTask.Companion.ARG_EXIT_PROGRAM, exitProgram)
            context.startService(i)
        }

        fun startTempFile(context: Context, srcLocation: Location) {
            try {
                val i = Intent(context, FileOpsService::class.java)
                i.setAction(ACTION_START_TEMP_FILE)
                LocationsManager.storePathsInIntent(i, srcLocation, listOf(srcLocation.currentPath))
                context.startService(i)
            } catch (e: IOException) {
                Logger.showAndLog(context, e)
            }
        }

        fun saveChangedFile(context: Context, files: SrcDstCollection?) {
            val i = Intent(context, FileOpsService::class.java)
            i.setAction(ACTION_SAVE_CHANGED_FILE)
            i.putExtra(ARG_RECORDS, files)
            context.startService(i)
        }

        fun prepareTempFile(context: Context, srcLocation: Location, paths: Collection<Path?>?) {
            val i = Intent(context, FileOpsService::class.java)
            i.setAction(ACTION_PREPARE_TEMP_FILE)
            LocationsManager.storePathsInIntent(i, srcLocation, paths)
            context.startService(i)
        }

        fun sendFile(
            context: Context,
            mimeType: String?,
            srcLocation: Location,
            paths: Collection<Path?>?
        ) {
            val i = Intent(context, FileOpsService::class.java)
            i.setAction(ACTION_SEND_TASK)
            LocationsManager.storePathsInIntent(i, srcLocation, paths)
            if (mimeType != null) i.putExtra(ActionSendTask.Companion.ARG_MIME_TYPE, mimeType)
            context.startService(i)
        }

        fun cancelTask(context: Context) {
            val i = Intent(context, FileOpsService::class.java)
            i.setAction(ACTION_CANCEL_TASK)
            context.startService(i)
        }

        protected const val TAG: String = "FileOpsService"

        const val ACTION_COPY: String = "copy"
        const val ACTION_MOVE: String = "move"
        const val ACTION_RECEIVE: String = "receive"
        const val ACTION_DELETE: String = "delete"
        const val ACTION_WIPE: String = "wipe"
        protected const val ACTION_START_TEMP_FILE: String = "start_temp_file"
        const val ACTION_SAVE_CHANGED_FILE: String = "save_changed_file"
        const val ACTION_PREPARE_TEMP_FILE: String = "prepare_temp_file"
        protected const val ACTION_SEND_TASK: String = "send"
        protected const val ACTION_CANCEL_TASK: String = "cancel_task"
        const val ACTION_CLEAR_TEMP_FOLDER: String = "clear_temp_folder"
        protected const val ACTION_CLOSE_CONTAINER: String = "close_container"

        const val ARG_RECORDS: String = "src_dst_records"
        const val ARG_OVERWRITE: String = "overwrite"

        var NOTIFICATION_COUNTER: Int = 1000
    }
}
