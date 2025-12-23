package com.sovworks.eds.android.filemanager.tasks

import android.app.Activity
import android.os.Bundle
import com.sovworks.eds.android.filemanager.fragments.FileListViewFragment
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager

class OpenAsContainerTask : CheckStartPathTask() {
    override fun getTaskCallbacks(activity: Activity): TaskCallbacks? {
        val f = fragmentManager.findFragmentByTag(FileListViewFragment.TAG) as FileListViewFragment
        return f?.openAsContainerTaskCallbacks
    }

    companion object {
        fun newInstance(locationLocation: Location, storeLink: Boolean): OpenAsContainerTask {
            val args = Bundle()
            args.putBoolean(ARG_STORE_LINK, storeLink)
            LocationsManager.storePathsInBundle(args, locationLocation, null)
            val f = OpenAsContainerTask()
            f.arguments = args
            return f
        }
    }
}
