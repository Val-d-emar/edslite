package com.sovworks.eds.android.locations.tasks

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Parcelable
import android.os.PowerManager
import com.sovworks.eds.android.R
import com.sovworks.eds.android.activities.SettingsBaseActivity
import com.sovworks.eds.android.errors.InputOutputException
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.android.errors.WrongPasswordOrBadContainerException
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.container.EDSLocationFormatter
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.fs.errors.WrongImageFormatException
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.Openable
import java.io.IOException

abstract class CreateEDSLocationTaskFragmentBase : TaskFragment() {
    override fun initTask(activity: Activity) {
        _context = activity.applicationContext
        _locationsManager = LocationsManager.getLocationsManager(_context)
    }

    protected var _context: Context? = null
    protected var _locationsManager: LocationsManager? = null

    override fun getTaskCallbacks(activity: Activity?): TaskCallbacks? {
        val f =
            fragmentManager.findFragmentByTag(SettingsBaseActivity.SETTINGS_FRAGMENT_TAG) as CreateEDSLocationFragment
        return f?.createLocationTaskCallbacks
    }

    @Throws(Exception::class)
    override fun doWork(state: TaskState) {
        state.setResult(0)
        val location = _locationsManager
            .getLocation(
                arguments.getParcelable<Parcelable>(ARG_LOCATION) as Uri?
            )

        if (!checkParams(state, location)) return
        val pm = _context
            .getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            toString()
        )
        wl.acquire()
        try {
            createEDSLocation(state, location)
        } finally {
            wl.release()
        }
    }

    @Throws(Exception::class)
    protected fun createEDSLocation(state: TaskState, locationLocation: Location?) {
        val f = createFormatter()
        val password = arguments.getParcelable<SecureBuffer>(Openable.PARAM_PASSWORD)
        try {
            initFormatter(state, f, password)
            f.format(locationLocation)
        } catch (e: WrongImageFormatException) {
            val e1 = WrongPasswordOrBadContainerException(
                _context
            )
            e1.initCause(e)
            throw e1
        } catch (e: IOException) {
            throw InputOutputException(_context, e)
        } catch (e: Exception) {
            throw UserException(
                _context!!,
                R.string.err_failed_creating_container, e
            )
        }
    }

    protected abstract fun createFormatter(): EDSLocationFormatter

    @Throws(Exception::class)
    protected open fun initFormatter(
        state: TaskState,
        formatter: EDSLocationFormatter,
        password: SecureBuffer?
    ) {
        formatter.context = _context
        formatter.setPassword(password)
        formatter.setProgressReporter { prc ->
            state.updateUI(prc)
            !state.isTaskCancelled
        }
    }

    @Throws(Exception::class)
    protected open fun checkParams(state: TaskState, locationLocation: Location): Boolean {
        if (!arguments.getBoolean(ARG_OVERWRITE, false)) {
            if (locationLocation.currentPath.exists()) {
                state.setResult(RESULT_REQUEST_OVERWRITE)
                return false
            }
        }
        return true
    }

    companion object {
        const val TAG: String =
            "com.sovworks.eds.android.locations.tasks.CreateEDSLocationTaskFragment"

        const val ARG_LOCATION: String = "com.sovworks.eds.android.LOCATION"
        const val ARG_CIPHER_NAME: String = "com.sovworks.eds.android.CIPHER_NAME"
        const val ARG_OVERWRITE: String = "com.sovworks.eds.android.OVERWRITE"

        const val RESULT_REQUEST_OVERWRITE: Int = 1
    }
}
