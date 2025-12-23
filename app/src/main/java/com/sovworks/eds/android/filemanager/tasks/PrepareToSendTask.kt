package com.sovworks.eds.android.filemanager.tasks

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.service.ActionSendTask
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.fs.Path
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.settings.GlobalConfig

class PrepareToSendTask : TaskFragment() {
    public override fun initTask(activity: Activity) {
        _context = activity.applicationContext
    }

    protected var _context: Context? = null

    @Throws(Exception::class)
    override fun doWork(state: TaskState) {
        if (GlobalConfig.isDebug()) Logger.debug("PrepareToSendTask args: $arguments")
        val paths = ArrayList<Path>()
        val location: Location = LocationsManager.getLocationsManager
        (_context).getFromBundle
        (
                arguments,
        paths
        )
        val uris = ArrayList<Uri>()
        val checkedPaths = ArrayList<Path>()
        var mime1: String? = null
        var mime2: String? = null
        for (p in paths) {
            if (p.isFile) {
                val uri = location.getDeviceAccessibleUri(p)
                if (uri != null) uris.add(uri)
                checkedPaths.add(p)
                val mimeType = FileOpsService.getMimeTypeFromExtension(_context, p)
                    .split("/".toRegex(), limit = 2).toTypedArray()
                if (mime1 == null) {
                    mime1 = mimeType[0]
                    mime2 = mimeType[1]
                } else if (mime1 != "*") {
                    if (mime1 != mimeType[0]) {
                        mime1 = "*"
                        mime2 = "*"
                    } else if (mime2 != "*") {
                        if (mime2 != mimeType[1]) mime2 = "*"
                    }
                }
            }
        }

        val result = PrepareSendResult()
        result.mimeType = if (mime1 != null && mime2 != null) ("$mime1/$mime2") else null
        result.location = location
        if (!checkedPaths.isEmpty()) {
            if (uris.size == checkedPaths.size) {
                result.urisToSend = uris
                if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) result.clipData =
                    CopyToClipboardTask.Companion.makeClipData(
                        _context!!, location, checkedPaths
                    )
            } else result.tempFilesToPrepare = checkedPaths
        }
        state.setResult(result)
    }

    override fun getTaskCallbacks(activity: Activity): TaskCallbacks {
        return object : TaskCallbacks {
            override fun onUpdateUI(state: Any) {
            }

            override fun onPrepare(args: Bundle) {
            }

            override fun onResumeUI(args: Bundle) {
            }

            override fun onSuspendUI(args: Bundle) {
            }

            override fun onCompleted(args: Bundle, result: Result) {
                try {
                    val res = result.result as PrepareSendResult
                    if (res.urisToSend != null) ActionSendTask.sendFiles(
                        activity,
                        res.urisToSend,
                        res.mimeType,
                        res.clipData
                    )
                    else if (res.tempFilesToPrepare != null) FileOpsService.sendFile(
                        activity,
                        res.mimeType,
                        res.location,
                        res.tempFilesToPrepare
                    )
                } catch (e: Throwable) {
                    Logger.showAndLog(activity, e)
                }
            }
        }
    }

    private class PrepareSendResult {
        var tempFilesToPrepare: ArrayList<Path>? = null
        var urisToSend: ArrayList<Uri>? = null
        var clipData: ClipData? = null
        var mimeType: String? = null
        var location: Location? = null
    }

    companion object {
        const val TAG: String = "PrepareToSendTask"

        fun newInstance(loc: Location, paths: Collection<Path?>?): PrepareToSendTask {
            val args = Bundle()
            LocationsManager.storePathsInBundle(args, loc, paths)
            val f = PrepareToSendTask()
            f.arguments = args
            return f
        }
    }
}