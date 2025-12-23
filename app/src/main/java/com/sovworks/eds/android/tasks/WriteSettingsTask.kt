package com.sovworks.eds.android.tasks

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.activities.SettingsBaseActivity
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.helpers.ProgressDialogTaskFragmentCallbacks
import com.sovworks.eds.android.locations.fragments.EDSLocationSettingsFragment
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.LocationsManager

class WriteSettingsTask : TaskFragment() {
    override fun initTask(activity: Activity) {
        super.initTask(activity)
        _context = activity.applicationContext
    }

    private var _context: Context? = null

    @Throws(Exception::class)
    override fun doWork(state: TaskState?) {
        val cont = LocationsManager.getLocationsManager(_context).getFromBundle(
            arguments, null
        ) as EDSLocation
        cont.applyInternalSettings()
        cont.writeInternalSettings()
    }

    override fun getTaskCallbacks(activity: Activity): TaskCallbacks? {
        val f =
            fragmentManager.findFragmentByTag(SettingsBaseActivity.SETTINGS_FRAGMENT_TAG) as EDSLocationSettingsFragment
                ?: return null
        return object : ProgressDialogTaskFragmentCallbacks(activity, R.string.saving_changes) {
            override fun onCompleted(args: Bundle?, result: Result?) {
                super.onCompleted(args, result)
                try {
                    result!!.result
                    if (args!!.getBoolean(ARG_FIN_ACTIVITY, false)) getActivity().finish()
                } catch (e: Throwable) {
                    Logger.Companion.showAndLog(_context, result!!.error!!)
                }
            }
        }
    }

    companion object {
        const val TAG: String = "com.sovworks.eds.android.tasks.WriteSettingsTask"
        const val ARG_FIN_ACTIVITY: String = "com.sovworks.eds.android.FIN_ACTIVITY"

        fun newInstance(cont: EDSLocation, finActivity: Boolean): WriteSettingsTask {
            val args = Bundle()
            args.putBoolean(ARG_FIN_ACTIVITY, finActivity)
            LocationsManager.storePathsInBundle(args, cont, null)
            val f = WriteSettingsTask()
            f.arguments = args
            return f
        }
    }
}
