package com.sovworks.eds.android.filemanager.tasks

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.locations.tasks.AddExistingContainerTaskFragment
import com.sovworks.eds.locations.LocationsManager

open class CheckStartPathTask : AddExistingContainerTaskFragment() {
    @Throws(Exception::class)
    override fun doWork(state: TaskState) {
        val lm = LocationsManager.getLocationsManager(_context)
        val loc = lm.getFromBundle(arguments, null)
        if (loc.currentPath.isFile) state.setResult(
            findOrCreateEDSLocation(
                lm, loc, arguments.getBoolean(
                    ARG_STORE_LINK
                )
            )
        )
        else state.setResult(null)
    }

    override fun getTaskCallbacks(activity: Activity): TaskCallbacks {
        return (activity as FileManagerActivity).checkStartPathCallbacks
    }

    companion object {
        fun newInstance(startUri: Uri?, storeLink: Boolean): CheckStartPathTask {
            val args = Bundle()
            args.putBoolean(ARG_STORE_LINK, storeLink)
            args.putParcelable(LocationsManager.PARAM_LOCATION_URI, startUri)
            val f = CheckStartPathTask()
            f.arguments = args
            return f
        }
    }
}
