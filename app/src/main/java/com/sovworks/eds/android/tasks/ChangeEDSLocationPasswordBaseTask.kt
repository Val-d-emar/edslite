package com.sovworks.eds.android.tasks

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.activities.SettingsBaseActivity
import com.sovworks.eds.android.fragments.PropertiesFragmentBase.getPropertiesView
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.helpers.ProgressDialogTaskFragmentCallbacks
import com.sovworks.eds.android.locations.fragments.EDSLocationSettingsFragment
import com.sovworks.eds.android.settings.views.PropertiesView.loadProperties
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.LocationsManager
import java.io.IOException

abstract class ChangeEDSLocationPasswordBaseTask : TaskFragment() {
    override fun initTask(activity: Activity) {
        _context = activity.applicationContext
        _location = LocationsManager.getLocationsManager(_context)
            .getFromBundle(arguments, null) as EDSLocation
    }

    protected var _location: EDSLocation? = null
    protected var _context: Context? = null

    @Throws(Exception::class)
    override fun doWork(state: TaskState?) {
        changeLocationPassword()
    }

    override fun getTaskCallbacks(activity: Activity): TaskCallbacks? {
        val f =
            fragmentManager.findFragmentByTag(SettingsBaseActivity.SETTINGS_FRAGMENT_TAG) as EDSLocationSettingsFragment
                ?: return null
        return object : ProgressDialogTaskFragmentCallbacks(activity, R.string.changing_password) {
            override fun onCompleted(args: Bundle?, result: Result?) {
                super.onCompleted(args, result)
                try {
                    result!!.result
                    f.getPropertiesView().loadProperties()
                } catch (e: Throwable) {
                    Logger.Companion.showAndLog(_context, result!!.error!!)
                }
            }
        }
    }

    @Throws(IOException::class, ApplicationException::class)
    protected abstract fun changeLocationPassword()
}
