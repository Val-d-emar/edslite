package com.sovworks.eds.android.locations.fragments

import android.app.Fragment
import android.os.Bundle
import android.text.format.Formatter
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.dialogs.PasswordDialog
import com.sovworks.eds.android.dialogs.PasswordDialogBase.PasswordReceiver
import com.sovworks.eds.android.fragments.PropertiesFragmentBase
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.fragments.TaskFragment.Result
import com.sovworks.eds.android.fragments.TaskFragment.TaskCallbacks
import com.sovworks.eds.android.helpers.ActivityResultHandler
import com.sovworks.eds.android.helpers.ProgressDialogTaskFragmentCallbacks
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment.LocationOpenerResultReceiver
import com.sovworks.eds.android.service.LocationsService
import com.sovworks.eds.android.settings.ButtonPropertyEditor
import com.sovworks.eds.android.settings.IntPropertyEditor
import com.sovworks.eds.android.settings.PropertiesHostWithLocation
import com.sovworks.eds.android.settings.PropertyEditor
import com.sovworks.eds.android.settings.StaticPropertyEditor
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.container.ChangePasswordPropertyEditor
import com.sovworks.eds.android.settings.container.OpenInReadOnlyModePropertyEditor
import com.sovworks.eds.android.settings.container.SavePIMPropertyEditor
import com.sovworks.eds.android.settings.container.SavePasswordPropertyEditor
import com.sovworks.eds.android.settings.container.UseExternalFileManagerPropertyEditor
import com.sovworks.eds.android.tasks.LoadLocationInfoTask
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.Openable
import java.util.Arrays

abstract class EDSLocationSettingsFragmentBase : PropertiesFragmentBase(),
    LocationOpenerResultReceiver, PropertiesHostWithLocation, PasswordReceiver {
    class LocationInfo {
        @JvmField
        var pathToLocation: String? = null
        @JvmField
        var totalSpace: Long = 0
        @JvmField
        var freeSpace: Long = 0
    }

    override fun onTargetLocationOpened(openerArgs: Bundle, location: Location) {
        onIntSettingsAvailable(location as EDSLocation)
    }

    override fun onTargetLocationNotOpened(openerArgs: Bundle) {
    }

    override fun onPause() {
        resHandler.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        resHandler.handle()
    }

    fun saveExternalSettings() {
        location!!.saveExternalSettings()
    }

    override fun getTargetLocation(): Location {
        return location!!
    }

    fun getChangePasswordTask(pd: PasswordDialog): TaskFragment {
        val t = createChangePasswordTaskInstance()
        t.arguments = getChangePasswordTaskArgs(pd)
        return t
    }

    protected open fun getChangePasswordTaskArgs(dlg: PasswordDialog): Bundle {
        val args = Bundle()
        val sb = SecureBuffer()
        val tmp = dlg.password
        sb.adoptData(tmp)
        args.putParcelable(Openable.PARAM_PASSWORD, sb)
        LocationsManager.storePathsInBundle(args, location, null)
        return args
    }

    protected abstract fun createChangePasswordTaskInstance(): TaskFragment

    val loadLocationInfoTaskCallbacks: TaskCallbacks
        get() = LoadLocationInfoTaskCallbacks()

    override fun onPasswordEntered(dlg: PasswordDialog) {
        val propertyId = dlg.arguments.getInt(PropertyEditor.ARG_PROPERTY_ID)
        val pr = propertiesView.getPropertyById(propertyId) as PasswordReceiver
        pr?.onPasswordEntered(dlg)
    }

    override fun onPasswordNotEntered(dlg: PasswordDialog) {
        val propertyId = dlg.arguments.getInt(PropertyEditor.ARG_PROPERTY_ID)
        val pr = propertiesView.getPropertyById(propertyId) as PasswordReceiver
        pr?.onPasswordNotEntered(dlg)
    }

    internal inner class LoadLocationInfoTaskCallbacks :
        ProgressDialogTaskFragmentCallbacks(activity, R.string.loading) {
        override fun onCompleted(args: Bundle?, result: Result) {
            super.onCompleted(args, result)
            try {
                this.locationInfo = result.result as LocationInfo?
                _propertiesView!!.setPropertyState(R.string.path_to_container, true)
                _propertiesView!!.setPropertyState(R.string.uri_of_the_container, true)
                _propertiesView!!.setPropertiesState(
                    Arrays.asList(
                        R.string.free_space, R.string.total_space
                    ), location!!.isOpenOrMounted
                )
                _propertiesView!!.loadProperties(
                    Arrays.asList(
                        R.string.path_to_container,
                        R.string.uri_of_the_container,
                        R.string.free_space,
                        R.string.total_space
                    ),
                    null
                )
            } catch (e: Throwable) {
                Logger.showAndLog(_context, e)
            }
        }
    }

    val resHandler: ActivityResultHandler = ActivityResultHandler()
    open var location: EDSLocation? = null
    var locationInfo: LocationInfo? = null
        protected set

    protected abstract val locationOpener: LocationOpenerBaseFragment

    protected fun initLoadLocationInfoTask(): LoadLocationInfoTask {
        return LoadLocationInfoTask.newInstance(location)
    }

    protected fun startLoadLocationInfoTask() {
        fragmentManager.beginTransaction
        ().add
        (
                initLoadLocationInfoTask(),
        LoadLocationInfoTask.TAG
        ).commitAllowingStateLoss
        ()
    }

    protected fun onIntSettingsAvailable(loc: EDSLocation?) {
        location = loc
        showInternalSettings()
        propertiesView.loadProperties()
        startLoadLocationInfoTask()
    }

    override fun createProperties() {
        location = LocationsManager.getLocationsManager
        (activity).getFromIntent
        (activity.intent, null) as EDSLocation?
        if (location == null) {
            activity.finish()
            return
        }
        _propertiesView!!.isInstantSave = true
        createAllProperties()
        initPropertiesState()
    }

    protected fun createAllProperties() {
        createStdProperties(ArrayList())
    }

    protected fun initPropertiesState() {
        if (location == null) _propertiesView!!.setPropertiesState(false)
        else {
            if (location!!.isOpenOrMounted) showInternalSettings()
            else hideInternalSettings()

            _propertiesView!!.setPropertyState(R.string.path_to_container, false)
            _propertiesView!!.setPropertyState(R.string.save_password, location!!.hasPassword())
            _propertiesView!!.setPropertyState(
                R.string.remember_kdf_iterations_multiplier,
                location!!.hasCustomKDFIterations()
            )
            _propertiesView!!.setPropertyState(
                R.string.use_external_file_manager,
                UserSettings.getSettings(activity).externalFileManagerInfo != null
            )
            startLoadLocationInfoTask()
        }
    }

    protected open fun createStdProperties(ids: MutableCollection<Int?>) {
        createInfoProperties(ids)
        createPasswordProperties(ids)
        createMiscProperties(ids)
    }

    protected fun createInfoProperties(ids: MutableCollection<Int?>) {
        ids.add(_propertiesView!!.addProperty(object :
            StaticPropertyEditor(this, R.string.path_to_container) {
            override fun loadText(): String {
                return if (this.locationInfo == null) "" else locationInfo!!.pathToLocation!!
            }
        }))
        ids.add(_propertiesView!!.addProperty(object :
            StaticPropertyEditor(this, R.string.uri_of_the_container) {
            override fun loadText(): String {
                return if (this.locationInfo == null) "" else location!!.locationUri.toString()
            }
        }))
        ids.add(_propertiesView!!.addProperty(object :
            StaticPropertyEditor(this, R.string.total_space) {
            override fun loadText(): String {
                if (!location!!.isOpenOrMounted || this.locationInfo == null) return ""
                return Formatter.formatFileSize(host.context, locationInfo!!.totalSpace)
            }
        }))
        ids.add(_propertiesView!!.addProperty(object :
            StaticPropertyEditor(this, R.string.free_space) {
            override fun loadText(): String {
                if (!location!!.isOpenOrMounted || this.locationInfo == null) return ""
                return Formatter.formatFileSize(host.context, locationInfo!!.freeSpace)
            }
        }))
    }

    protected fun createPasswordProperties(ids: MutableCollection<Int?>) {
        ids.add(_propertiesView!!.addProperty(ChangePasswordPropertyEditor(this)))
        ids.add(_propertiesView!!.addProperty(SavePasswordPropertyEditor(this)))
        ids.add(_propertiesView!!.addProperty(SavePIMPropertyEditor(this)))
    }

    protected fun createMiscProperties(ids: MutableCollection<Int?>) {
        ids.add(_propertiesView!!.addProperty(object : IntPropertyEditor(
            this, R.string.auto_close_container, R.string.auto_close_container_desc,
            tag
        ) {
            override fun loadValue(): Int {
                return location!!.externalSettings.autoCloseTimeout / 60000
            }

            override fun saveValue(value: Int) {
                location!!.externalSettings.autoCloseTimeout = value * 60000
                saveExternalSettings()
                LocationsService.registerInactiveContainerCheck(context, this.location)
            }

            override fun getDialogViewResId(): Int {
                return R.layout.settings_edit_num_lim4
            }
        }))
        ids.add(_propertiesView!!.addProperty(OpenInReadOnlyModePropertyEditor(this)))
        ids.add(_propertiesView!!.addProperty(object : ButtonPropertyEditor(
            this,
            R.string.internal_container_settings,
            R.string.internal_container_settings_desc,
            R.string.open
        ) {
            override fun onButtonClick() {
                val openerArgs = Bundle()
                LocationsManager.storePathsInBundle(openerArgs, this.location, null)
                openerArgs.putString(LocationOpenerBaseFragment.PARAM_RECEIVER_FRAGMENT_TAG, tag)
                val opener: Fragment = this.locationOpener
                opener.arguments = openerArgs
                fragmentManager.beginTransaction
                ().add
                (opener, LocationOpenerBaseFragment.getOpenerTag(_location)).commit
                ()
            }
        }))
        ids.add(_propertiesView!!.addProperty(UseExternalFileManagerPropertyEditor(this)))
    }

    protected fun showInternalSettings() {
        setInternalPropertiesEnabled(true)
    }

    protected fun hideInternalSettings() {
        setInternalPropertiesEnabled(false)
    }

    protected fun setInternalPropertiesEnabled(enabled: Boolean) {
        _propertiesView!!.setPropertyState(R.string.free_space, enabled && locationInfo != null)
        _propertiesView!!.setPropertyState(R.string.total_space, enabled && locationInfo != null)
        _propertiesView!!.setPropertyState(R.string.change_container_password, enabled)
        _propertiesView!!.setPropertyState(R.string.internal_container_settings, !enabled)
    }
}
