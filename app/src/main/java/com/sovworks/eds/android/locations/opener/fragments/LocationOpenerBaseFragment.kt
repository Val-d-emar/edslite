package com.sovworks.eds.android.locations.opener.fragments

import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnCancelListener
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.text.Html
import android.widget.Toast
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.dialogs.ProgressDialog
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.fragments.TaskFragment.EventType.Removed
import com.sovworks.eds.android.fragments.TaskFragment.Result
import com.sovworks.eds.android.fragments.TaskFragment.TaskCallbacks
import com.sovworks.eds.android.fragments.TaskFragment.TaskState
import com.sovworks.eds.android.helpers.ActivityResultHandler
import com.sovworks.eds.android.helpers.ContainerOpeningProgressReporter
import com.sovworks.eds.android.locations.OpenersRegistry
import com.sovworks.eds.android.service.LocationsService
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import java.io.IOException
import java.util.concurrent.CancellationException

open class LocationOpenerBaseFragment : Fragment() {
    interface LocationOpenerResultReceiver {
        fun onTargetLocationOpened(openerArgs: Bundle?, location: Location?)
        fun onTargetLocationNotOpened(openerArgs: Bundle?)
    }

    open class OpenLocationTaskFragment : TaskFragment() {
        @JvmField
        protected var _context: Context? = null
        protected var _locationsManager: LocationsManager? = null
        protected var _openingProgressReporter: ProgressReporter? = null

        override fun initTask(activity: Activity) {
            _context = activity.applicationContext
            _locationsManager = LocationsManager.getLocationsManager(activity)
            _openingProgressReporter = ProgressReporter(_context)
        }

        @Throws(Exception::class)
        override fun doWork(taskState: TaskState) {
            val pm = _context!!.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, toString())
            wl.acquire()
            try {
                if (_openingProgressReporter != null) _openingProgressReporter!!.setTaskState(
                    taskState
                )
                val location = getTargetLocation()
                procLocation(taskState, location, arguments)
                LocationsService.startService(_context)
                if (location.isFileSystemOpen && !location.currentPath.exists()) location.currentPath =
                    location.fs.rootPath
                taskState.setResult(location)
            } finally {
                wl.release()
            }
        }

        @Throws(Exception::class)
        protected fun getTargetLocation(): Location {
            val locationUri = arguments.getParcelable<Uri>(LocationsManager.PARAM_LOCATION_URI)
            return _locationsManager!!.getLocation(locationUri)
        }

        override fun getTaskCallbacks(activity: Activity?): TaskCallbacks? {
            val f =
                fragmentManager.findFragmentByTag(arguments.getString(ARG_OPENER_TAG)) as LocationOpenerBaseFragment
            return f?.openLocationTaskCallbacks
        }

        @Throws(Exception::class)
        protected open fun procLocation(state: TaskState?, location: Location, param: Bundle) {
            var err: Exception? = null
            try {
                openFS(location, param)
            } catch (e: Exception) {
                err = e
            }
            LocationsManager.broadcastLocationChanged(_context, location)
            if (err != null) throw err
        }

        @Throws(IOException::class)
        protected fun openFS(location: Location, param: Bundle?) {
            location.fs
        }

        override fun detachTask() {
            val fm = fragmentManager
            if (fm != null) {
                val trans = fm.beginTransaction()
                trans.remove(this)
                val f =
                    fm.findFragmentByTag(arguments.getString(ARG_OPENER_TAG)) as LocationOpenerBaseFragment
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
            const val ARG_OPENER_TAG: String = "com.sovworks.eds.android.OPENER_TAG"

            const val TAG: String =
                "com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment.OpenLocationTaskFragment"
        }
    }

    class ProgressReporter(private val _context: Context?) : ContainerOpeningProgressReporter {
        fun setTaskState(taskState: TaskState?) {
            _taskState = taskState
        }

        override fun setCurrentKDFName(name: String?) {
            _hashName = name
            updateText()
        }

        override fun setCurrentEncryptionAlgName(name: String?) {
            _encAlgName = name
            updateText()
        }

        override fun setContainerFormatName(name: String?) {
            _formatName = name
            updateText()
        }

        override fun setIsHidden(`val`: Boolean) {
            _isHidden = `val`
            updateText()
        }

        override fun setText(text: CharSequence?) {
            statusText = text
            updateUI()
        }

        override fun setProgress(progress: Int) {
            _progress = progress
            updateUI()
        }

        override val isCancelled: Boolean
            get() = _taskState != null && _taskState!!.isTaskCancelled

        fun getProgress(): Int {
            return _progress
        }

        private var _taskState: TaskState? = null
        private var _formatName: String? = null
        private var _hashName: String? = null
        private var _encAlgName: String? = null
        var statusText: CharSequence? = null
            private set
        private var _isHidden = false
        private var _progress = 0
        private var _prevUIUpdateTime: Long = 0

        private fun updateText() {
            setText(makeStatusText())
        }

        private fun updateUI() {
            if (_taskState != null) {
                val cur = SystemClock.elapsedRealtime()
                if (cur - _prevUIUpdateTime > 500) {
                    _prevUIUpdateTime = cur
                    _taskState!!.updateUI(this)
                }
            }
        }

        private fun makeStatusText(): CharSequence {
            val statusString = StringBuilder()
            if (_formatName != null) {
                var fn: String = _formatName
                if (_isHidden) fn += " (" + _context!!.getString(R.string.hidden) + ')'
                statusString.append(_context!!.getString(R.string.container_format_is, fn))
            }
            if (_encAlgName != null) statusString.append(
                _context!!.getString(
                    R.string.encryption_alg_is,
                    _encAlgName
                )
            )
            if (_hashName != null) statusString.append(
                _context!!.getString(
                    R.string.kdf_base_hash_func_is,
                    _hashName
                )
            )
            return Html.fromHtml(statusString.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (fragmentManager.findFragmentByTag(openLocationTaskTag) == null) openLocation()
    }

    override fun onPause() {
        _resHandler.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        _resHandler.handle()
    }

    val openLocationTaskCallbacks: TaskCallbacks
        get() = OpenLocationTaskCallbacks()

    protected inner class OpenLocationTaskCallbacks : TaskCallbacks {
        override fun onPrepare(args: Bundle?) {
        }

        override fun onResumeUI(args: Bundle?) {
            _dialog =
                ProgressDialog.showDialog(fragmentManager, getString(R.string.opening_container))
            _dialog!!.isCancelable = true
            _dialog!!.setOnCancelListener(object : OnCancelListener {
                override fun onCancel(dialog: DialogInterface) {
                    val f = fragmentManager.findFragmentByTag(
                        this.openLocationTaskTag
                    ) as OpenLocationTaskFragment
                    f?.cancel()
                }
            })
        }

        override fun onSuspendUI(args: Bundle?) {
            _dialog!!.dismiss()
        }

        override fun onCompleted(args: Bundle?, result: Result) {
            procOpenLocationTaskResult(args, result)
        }

        override fun onUpdateUI(state: Any?) {
            val r = state as ProgressReporter?
            if (r != null) {
                _dialog!!.setText(r.statusText)
                _dialog!!.setProgress(r.getProgress())
            }
        }

        private var _dialog: ProgressDialog? = null
    }

    protected val _resHandler: ActivityResultHandler = ActivityResultHandler()

    protected open val openLocationTask: TaskFragment
        get() = OpenLocationTaskFragment()

    protected val openLocationTaskTag: String
        get() = getOpenLocationTaskTag(targetLocation!!)

    protected open fun initOpenLocationTaskParams(location: Location): Bundle? {
        val b = Bundle()
        b.putString(
            OpenLocationTaskFragment.ARG_OPENER_TAG,
            tag
        )
        LocationsManager.storePathsInBundle(b, location, null)
        return b
    }

    protected open val targetLocation: Location?
        get() = LocationsManager.getFromBundle(
            arguments,
            LocationsManager.getLocationsManager(activity),
            null
        )

    protected open fun openLocation() {
        finishOpener(true, targetLocation)
    }

    protected open fun onLocationOpened(location: Location?) {
        val rec = resultReceiver
        rec?.onTargetLocationOpened(arguments, location)
    }

    val resultReceiver: LocationOpenerResultReceiver?
        get() {
            val recTag =
                if (arguments != null) arguments.getString(PARAM_RECEIVER_FRAGMENT_TAG) else null
            return if (recTag != null) fragmentManager.findFragmentByTag(recTag) as LocationOpenerResultReceiver else null
        }

    protected fun onLocationNotOpened() {
        val rec = resultReceiver
        rec?.onTargetLocationNotOpened(arguments)
    }

    protected fun finishOpener(opened: Boolean, location: Location?) {
        fragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
        if (opened) onLocationOpened(location)
        else onLocationNotOpened()
    }

    protected fun procOpenLocationTaskResult(args: Bundle?, result: Result) {
        try {
            val location = result.result as Location?
            if (location!!.isReadOnly) Toast.makeText(
                activity,
                R.string.container_opened_read_only,
                Toast.LENGTH_LONG
            ).show()
            finishOpener(true, location)
            return
        } catch (ignored: CancellationException) {
        } catch (e: Throwable) {
            Logger.showAndLog(activity, e)
        }
        finishOpener(false, null)
    }

    protected fun startOpeningTask(args: Bundle?) {
        val f = openLocationTask
        f.arguments = args
        fragmentManager.beginTransaction().add(f, openLocationTaskTag).commit()
    }

    companion object {
        @JvmStatic
        fun getOpenerTag(location: Location): String {
            return TAG + location.id
        }

        fun getOpenLocationTaskTag(location: Location): String {
            return OpenLocationTaskFragment.TAG + location.id
        }

        @JvmStatic
        fun getDefaultOpenerForLocation(location: Location?): LocationOpenerBaseFragment {
            return OpenersRegistry.getDefaultOpenerForLocation(location)
        }

        const val PARAM_RECEIVER_FRAGMENT_TAG: String =
            "com.sovworks.eds.android.locations.opener.fragments.LocationOpenerFragment.RECEIVER_FRAGMENT_TAG"

        private const val TAG =
            "com.sovworks.eds.android.locations.opener.fragments.LocationOpenerFragment"
    }
}
