package com.sovworks.eds.android.tasks

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.sovworks.eds.android.activities.SettingsBaseActivity
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.locations.fragments.EDSLocationSettingsFragment
import com.sovworks.eds.android.locations.fragments.EDSLocationSettingsFragmentBase.LocationInfo
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.LocationsManager

class LoadLocationInfoTask : TaskFragment() {
    override fun initTask(activity: Activity) {
        super.initTask(activity)
        _context = activity.applicationContext
    }

    @Throws(Exception::class)
    override fun doWork(state: TaskState) {
        val cont = LocationsManager.getLocationsManager(_context).getFromBundle(
            arguments, null
        ) as EDSLocation
        val info = initParams()
        fillInfo(cont, info)
        state.setResult(info)
    }

    override fun getTaskCallbacks(activity: Activity?): TaskCallbacks? {
        val f =
            fragmentManager.findFragmentByTag(SettingsBaseActivity.SETTINGS_FRAGMENT_TAG) as EDSLocationSettingsFragment
        return f?.loadLocationInfoTaskCallbacks
    }

    protected fun initParams(): LocationInfo {
        return LocationInfo()
    }

    @Throws(Exception::class)
    protected fun fillInfo(location: EDSLocation, info: LocationInfo) {
        info.pathToLocation = location.location.toString()
        if (location.isOpenOrMounted) {
            info.totalSpace = location.currentPath.directory.totalSpace
            info.freeSpace = location.currentPath.directory.freeSpace
        }
    }

    protected var _context: Context? = null

    companion object {
        const val TAG: String = "com.sovworks.eds.android.tasks.LoadLocationInfoTask"

        fun newInstance(location: EDSLocation): LoadLocationInfoTask {
            val args = Bundle()
            LocationsManager.storePathsInBundle(args, location, null)
            val f = LoadLocationInfoTask()
            f.arguments = args
            return f
        }
    }
}
