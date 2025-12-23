package com.sovworks.eds.android.locations.fragments

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.fragments.PropertiesFragmentBase
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.fragments.TaskFragment.Result
import com.sovworks.eds.android.fragments.TaskFragment.TaskCallbacks
import com.sovworks.eds.android.helpers.ActivityResultHandler
import com.sovworks.eds.android.helpers.ProgressDialogTaskFragmentCallbacks
import com.sovworks.eds.android.locations.dialogs.OverwriteContainerDialog
import com.sovworks.eds.android.locations.tasks.CreateContainerTaskFragmentBase
import com.sovworks.eds.android.locations.tasks.CreateEDSLocationTaskFragment
import com.sovworks.eds.android.settings.PropertiesHostWithStateBundle
import com.sovworks.eds.android.settings.container.ExistingContainerPropertyEditor
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.Openable
import java.util.concurrent.CancellationException

abstract class CreateEDSLocationFragmentBase : PropertiesFragmentBase(),
    PropertiesHostWithStateBundle {
    override fun onCreate(state: Bundle?) {
        if (state != null) _state.putAll(state)
        super.onCreate(state)
        setHasOptionsMenu(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putAll(_state)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.create_location_menu, menu)
        menu.findItem(R.id.confirm).setTitle(R.string.create_new_container)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val mi = menu.findItem(R.id.confirm)
        mi.setVisible(_state.containsKey(ARG_ADD_EXISTING_LOCATION))
        mi.setTitle(if (_state.getBoolean(ARG_ADD_EXISTING_LOCATION)) R.string.add_container else R.string.create_new_container)
        val enabled = checkParams()
        mi.setEnabled(enabled)
        @Suppress("deprecation") val sld =
            activity.resources.getDrawable(R.drawable.ic_menu_done) as StateListDrawable
        if (sld != null) {
            sld.setState(if (enabled) intArrayOf(android.R.attr.state_enabled) else IntArray(0))
            mi.setIcon(sld.current)
        }
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.confirm -> {
                if (_state.getBoolean(ARG_ADD_EXISTING_LOCATION)) startAddLocationTask()
                else startCreateLocationTask()
                return true
            }

            else -> return super.onOptionsItemSelected(menuItem)
        }
    }

    override fun onPause() {
        resHandler.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        resHandler.handle()
    }

    override fun onDestroy() {
        super.onDestroy()
        val sb = _state.getParcelable<SecureBuffer>(Openable.PARAM_PASSWORD)
        if (sb != null) {
            sb.close()
            _state.remove(Openable.PARAM_PASSWORD)
        }
    }

    override fun initProperties(state: Bundle?) {
        _propertiesView!!.isInstantSave = true
        super.initProperties(state)
    }

    override fun getState(): Bundle {
        return _state
    }

    fun setOverwrite(`val`: Boolean) {
        _state.putBoolean(CreateContainerTaskFragmentBase.ARG_OVERWRITE, `val`)
    }

    fun startCreateLocationTask() {
        try {
            _propertiesView!!.saveProperties()
            val task = createCreateLocationTask()
            task.arguments = _state
            fragmentManager.beginTransaction().add(task, CreateEDSLocationTaskFragment.TAG).commit()
        } catch (e: Exception) {
            Logger.showAndLog(activity, e)
        }
    }

    fun startAddLocationTask() {
        try {
            _propertiesView!!.saveProperties()
            fragmentManager.beginTransaction
            ().add
            (createAddExistingLocationTask(), AddExistingContainerTaskFragment.TAG).commit()
        } catch (e: Exception) {
            Logger.showAndLog(activity, e)
        }
    }

    fun showAddExistingLocationProperties() {
        _state.putBoolean(ARG_ADD_EXISTING_LOCATION, true)
        _propertiesView!!.setPropertiesState(false)
        _propertiesView!!.setPropertyState(R.string.path_to_container, true)
        _propertiesView!!.setPropertyState(R.string.container_format, true)
    }

    open fun showCreateNewLocationProperties() {
        _state.putBoolean(ARG_ADD_EXISTING_LOCATION, false)
        _propertiesView!!.setPropertiesState(false)
    }

    val addExistingEDSLocationTaskCallbacks: TaskCallbacks
        get() = object : ProgressDialogTaskFragmentCallbacks(activity, R.string.loading) {
            override fun onCompleted(
                args: Bundle?,
                result: Result?
            ) {
                try {
                    val loc =
                        result!!.result as Location?
                    LocationsManager.broadcastLocationAdded(context, loc)
                    val res = Intent()
                    res.setData(loc!!.locationUri)
                    activity.setResult(Activity.RESULT_OK, res)
                    activity.finish()
                } catch (ignored: CancellationException) {
                } catch (e: Throwable) {
                    Logger.showAndLog(activity, e)
                }
            }
        }
    val createLocationTaskCallbacks: TaskCallbacks
        get() = CreateLocationTaskCallbacks()

    val resHandler: ActivityResultHandler = ActivityResultHandler()
    protected val _state: Bundle = Bundle()

    protected abstract fun createAddExistingLocationTask(): TaskFragment
    protected abstract fun createCreateLocationTask(): TaskFragment

    protected inner class CreateLocationTaskCallbacks : TaskCallbacks {
        override fun onPrepare(args: Bundle?) {
        }

        override fun onResumeUI(args: Bundle?) {
            _dialog = ProgressDialog(context)
            _dialog!!.setMessage(getText(R.string.creating_container))
            _dialog!!.isIndeterminate = true
            _dialog!!.setCancelable(true)
            _dialog!!.setOnCancelListener {
                val f = fragmentManager
                    .findFragmentByTag(CreateContainerTaskFragmentBase.TAG) as CreateEDSLocationTaskFragment
                f?.cancel()
            }
            _dialog!!.show()
        }

        override fun onSuspendUI(args: Bundle?) {
            _dialog!!.dismiss()
        }

        override fun onCompleted(args: Bundle?, result: Result) {
            if (result.isCancelled) return
            try {
                val res = result.result as Int
                if (res == CreateContainerTaskFragmentBase.RESULT_REQUEST_OVERWRITE) OverwriteContainerDialog
                    .showDialog(fragmentManager)
                else {
                    activity.setResult(Activity.RESULT_OK)
                    activity.finish()
                }
            } catch (e: Throwable) {
                Logger.showAndLog(activity, result.error)
            }
        }

        override fun onUpdateUI(state: Any?) {
        }

        private var _dialog: ProgressDialog? = null
    }

    override fun createProperties() {
        createStartProperties()
        createNewLocationProperties()
        createExtProperties()

        if (_state.containsKey(ARG_ADD_EXISTING_LOCATION)) {
            if (_state.getBoolean(ARG_ADD_EXISTING_LOCATION)) showAddExistingLocationProperties()
            else showCreateNewLocationProperties()
        } else showAddExistingLocationRequestProperties()
    }

    protected fun createStartProperties() {
        _propertiesView!!.addProperty(ExistingContainerPropertyEditor(this))
    }

    protected open fun createNewLocationProperties() {
    }

    protected fun createExtProperties() {
    }

    protected fun checkParams(): Boolean {
        val loc =
            if (_state.containsKey(CreateContainerTaskFragmentBase.ARG_LOCATION)) _state.getParcelable<Parcelable>(
                CreateContainerTaskFragmentBase.ARG_LOCATION
            ) as Uri? else null
        return loc != null && !loc.toString().isEmpty()
    }

    protected fun showAddExistingLocationRequestProperties() {
        _propertiesView!!.setPropertiesState(false)
        _propertiesView!!.setPropertyState(
            R.string.create_new_container_or_add_existing_container,
            true
        )
    }

    companion object {
        const val ARG_ADD_EXISTING_LOCATION: String =
            "com.sovworks.eds.android.ADD_EXISTING_CONTAINER"
    }
}
