package com.sovworks.eds.android.filemanager.tasks

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager

class RenameFileTask : TaskFragment() {
    public override fun initTask(activity: Activity) {
        _context = activity.applicationContext
    }

    @Throws(Exception::class)
    override fun doWork(state: TaskState) {
        val newName = arguments.getString(ARG_NEW_NAME)
        val target = LocationsManager.getLocationsManager(_context).getFromBundle(
            arguments, null
        )
        val path = target.currentPath
        if (path.isFile) path.file.rename(newName)
        else if (path.isDirectory) path.directory.rename(newName)
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
                    result.result
                    activity.sendBroadcast(Intent(FileOpsService.BROADCAST_FILE_OPERATION_COMPLETED))
                } catch (e: Throwable) {
                    Logger.showAndLog(activity, e)
                }
            }
        }
    }

    private var _context: Context? = null

    companion object {
        const val TAG: String = "RenameFileTask"

        const val ARG_NEW_NAME: String = "com.sovworks.eds.android.NEW_NAME"

        fun newInstance(target: Location, newName: String?): RenameFileTask {
            val args = Bundle()
            args.putString(ARG_NEW_NAME, newName)
            LocationsManager.storePathsInBundle(args, target, null)
            val f = RenameFileTask()
            f.arguments = args
            return f
        }
    }
}