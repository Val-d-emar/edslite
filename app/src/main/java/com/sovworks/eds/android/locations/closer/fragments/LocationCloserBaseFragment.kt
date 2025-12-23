package com.sovworks.eds.android.locations.closer.fragments

import android.app.Activity
import android.app.Fragment
import android.app.ProgressDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.fragments.TaskFragment.EventType.Removed
import com.sovworks.eds.android.fragments.TaskFragment.Result
import com.sovworks.eds.android.fragments.TaskFragment.TaskCallbacks
import com.sovworks.eds.android.helpers.ActivityResultHandler
import com.sovworks.eds.android.locations.dialogs.ForceCloseDialog
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import java.util.concurrent.CancellationException

open class LocationCloserBaseFragment : Fragment() {
    interface CloseLocationReceiver {
        fun onTargetLocationClosed(location: Location?, closeTaskArgs: Bundle?)
        fun onTargetLocationNotClosed(location: Location?, closeTaskArgs: Bundle?)
    }

    open class CloseLocationTaskFragment : TaskFragment() {
        protected var _context: Context? = null
        protected var _locationsManager: LocationsManager? = null

        override fun initTask(activity: Activity) {
            _context = activity.applicationContext
            _locationsManager = LocationsManager.getLocationsManager(_context)
        }

        @Throws(Exception::class)
        override fun doWork(taskState: TaskState) {
            val pm = _context!!.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, toString())
            wl?.acquire(30000)
            try {
                val locationUri = arguments.getParcelable<Uri>(LocationsManager.PARAM_LOCATION_URI)
                val location = _locationsManager!!.getLocation(locationUri)
                procLocation(taskState, location)
                taskState.setResult(location)
            } finally {
                wl?.release()
            }
        }

        override fun getTaskCallbacks(activity: Activity?): TaskCallbacks? {
            val f =
                fragmentManager.findFragmentByTag(arguments.getString(ARG_CLOSER_TAG)) as LocationCloserBaseFragment
            return f?.closeLocationTaskCallbacks
        }

        @Throws(Exception::class)
        protected open fun procLocation(state: TaskState?, location: Location) {
            LocationsManager.broadcastLocationChanged(_context, location)
        }

        override fun detachTask() {
            val fm = fragmentManager
            if (fm != null) {
                val trans = fm.beginTransaction()
                trans.remove(this)
                val f =
                    fm.findFragmentByTag(arguments.getString(ARG_CLOSER_TAG)) as LocationCloserBaseFragment
                if (f != null) trans.remove(f)
                trans.commitAllowingStateLoss()
                Logger.debug(
                    String.format(
                        "TaskFragment %s has been removed from the fragment manager",
                        this
                    )
                )
                onEvent(
                    Removed,
                    this
                )
            }
        }

        companion object {
            const val ARG_CLOSER_TAG: String = "com.sovworks.eds.android.CLOSER_TAG"

            const val TAG: String =
                "com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment.CloseLocationTaskFragment"
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (fragmentManager.findFragmentByTag(closeLocationTaskTag) == null) closeLocation()
    }

    override fun onPause() {
        _resHandler.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        _resHandler.handle()
    }


    val closeLocationTaskCallbacks: TaskCallbacks
        get() = CloseLocationTaskCallbacks()

    protected inner class CloseLocationTaskCallbacks : TaskCallbacks {
        override fun onPrepare(args: Bundle?) {
        }

        override fun onResumeUI(args: Bundle?) {
            val activity = activity
            _dialog = ProgressDialog(activity)
            _dialog!!.setMessage(activity.getText(R.string.closing))
            _dialog!!.isIndeterminate = true
            _dialog!!.setCancelable(false)
            _dialog!!.setOnCancelListener {
                val f =
                    fragmentManager.findFragmentByTag(CloseLocationTaskFragment.TAG) as CloseLocationTaskFragment
                f?.cancel()
            }
            _dialog!!.show()
        }

        override fun onSuspendUI(args: Bundle?) {
            _dialog!!.dismiss()
        }

        override fun onCompleted(args: Bundle, result: Result) {
            procCloseLocationTaskResult(args, result)
        }

        override fun onUpdateUI(state: Any?) {
        }

        private var _dialog: ProgressDialog? = null
    }

    protected val _resHandler: ActivityResultHandler = ActivityResultHandler()

    protected open val closeLocationTask: TaskFragment
        get() = CloseLocationTaskFragment()

    protected val closeLocationTaskTag: String
        get() = getCloseLocationTaskTag(targetLocation!!)

    protected fun closeLocation() {
        startClosingTask(initCloseLocationTaskParams(targetLocation!!))
    }

    protected fun initCloseLocationTaskParams(location: Location): Bundle {
        val b = Bundle()
        b.putString(
            CloseLocationTaskFragment.ARG_CLOSER_TAG,
            tag
        )
        if (arguments.containsKey(ARG_FORCE_CLOSE)) b.putBoolean(
            ARG_FORCE_CLOSE, arguments.getBoolean(
                ARG_FORCE_CLOSE, false
            )
        )
        LocationsManager.storePathsInBundle(b, location, null)
        return b
    }

    protected val targetLocation: Location?
        get() = LocationsManager.getFromBundle(
            arguments,
            LocationsManager.getLocationsManager(activity),
            null
        )

    protected fun finishCloser(closed: Boolean, location: Location?, closeTaskArgs: Bundle?) {
        fragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
        if (closed) onLocationClosed(location, closeTaskArgs)
        else onLocationNotClosed(location, closeTaskArgs)
    }

    protected fun onLocationClosed(location: Location?, closeTaskArgs: Bundle?) {
        val recTag =
            if (arguments != null) arguments.getString(PARAM_RECEIVER_FRAGMENT_TAG) else null
        if (recTag != null) {
            val rec = fragmentManager.findFragmentByTag(recTag) as CloseLocationReceiver
            rec?.onTargetLocationClosed(location, closeTaskArgs)
        }
    }

    protected fun onLocationNotClosed(location: Location?, closeTaskArgs: Bundle?) {
        val recTag =
            if (arguments != null) arguments.getString(PARAM_RECEIVER_FRAGMENT_TAG) else null
        if (recTag != null) {
            val rec = fragmentManager.findFragmentByTag(recTag) as CloseLocationReceiver
            rec?.onTargetLocationNotClosed(location, closeTaskArgs)
        }
    }

    protected fun procCloseLocationTaskResult(args: Bundle, result: Result) {
        try {
            finishCloser(true, result.result as Location?, args)
        } catch (ignored: CancellationException) {
        } catch (e: Throwable) {
            if (!args.getBoolean(ARG_FORCE_CLOSE, false)) {
                Logger.log(e)
                ForceCloseDialog.showDialog(
                    fragmentManager,
                    tag,
                    targetLocation!!.title,
                    javaClass.name,
                    arguments
                )
                finishCloser(false, targetLocation, args)
            } else {
                Logger.showAndLog(activity, e)
                finishCloser(false, targetLocation, args)
            }
        }
    }

    protected fun startClosingTask(args: Bundle?) {
        val f = closeLocationTask
        f.arguments = args
        fragmentManager.beginTransaction().add(f, closeLocationTaskTag).commit()
    }

    companion object {
        const val ARG_FORCE_CLOSE: String = "com.sovworks.eds.android.FORCE_CLOSE"

        @JvmStatic
        fun getCloserTag(location: Location): String {
            return TAG + location.id
        }

        fun getCloseLocationTaskTag(location: Location): String {
            return CloseLocationTaskFragment.TAG + location.id
        }

        @JvmStatic
        fun getDefaultCloserForLocation(location: Location?): LocationCloserBaseFragment {
            return ClosersRegistry.getDefaultCloserForLocation(location)
        }

        const val PARAM_RECEIVER_FRAGMENT_TAG: String =
            "com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment.RECEIVER_FRAGMENT_TAG"

        private const val TAG =
            "com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment"
    }
}
