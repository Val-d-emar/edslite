package com.sovworks.eds.android.settings.fragments

import android.os.Bundle
import com.sovworks.eds.android.fragments.PropertiesFragmentBase
import com.sovworks.eds.android.settings.PropertiesHostWithLocation
import com.sovworks.eds.android.settings.PropertiesHostWithStateBundle
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.container.OpenInReadOnlyModePropertyEditor
import com.sovworks.eds.android.settings.container.PIMPropertyEditor
import com.sovworks.eds.android.settings.container.UseExternalFileManagerPropertyEditor
import com.sovworks.eds.locations.ContainerLocation
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.Openable
import com.sovworks.eds.settings.Settings

abstract class OpeningOptionsFragmentBase : PropertiesFragmentBase(), PropertiesHostWithStateBundle,
    PropertiesHostWithLocation {
    fun saveExternalSettings() {
        _location!!.saveExternalSettings()
    }

    override val targetLocation: Location?
        get() = _location

    override fun createProperties() {
        _location = LocationsManager.getLocationsManager
        (activity).getFromIntent
        (activity.intent, null) as Openable?
        if (_location == null) {
            activity.finish()
            return
        }
        _settings = UserSettings.getSettings(activity)
        _propertiesView.setInstantSave(true)
        val extras = activity.intent.extras
        if (extras != null) state.putAll(extras)
        createOpenableProperties()
        if (_location is EDSLocation) createEDSLocationProperties()
        if (_location is ContainerLocation) createContainerProperties()
    }

    protected var _location: Openable? = null
    protected var _settings: Settings? = null

    protected fun createEDSLocationProperties() {
        _propertiesView!!.addProperty(OpenInReadOnlyModePropertyEditor(this))
    }

    protected fun createOpenableProperties() {
        var id = _propertiesView!!.addProperty(PIMPropertyEditor(this))
        if (!_location!!.hasCustomKDFIterations()) _propertiesView!!.setPropertyState(id, false)
        id = _propertiesView!!.addProperty(UseExternalFileManagerPropertyEditor(this))
        if (_settings!!.externalFileManagerInfo == null) _propertiesView!!.setPropertyState(
            id,
            false
        )
    }

    protected fun createContainerProperties() {
    }

    override val state: Bundle = Bundle()
}
