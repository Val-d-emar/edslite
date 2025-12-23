package com.sovworks.eds.android.locations.fragments

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.fragments.PropertiesFragmentBase
import com.sovworks.eds.android.settings.PropertiesHostWithLocation
import com.sovworks.eds.android.settings.TextPropertyEditor
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.container.UseExternalFileManagerPropertyEditor
import com.sovworks.eds.locations.LocationBase
import com.sovworks.eds.locations.LocationsManager

abstract class LocationSettingsBaseFragment : PropertiesFragmentBase(), PropertiesHostWithLocation {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        _location = LocationsManager.getLocationsManager(activity).getFromIntent(
            activity.intent,
            null
        ) as LocationBase
        setHasOptionsMenu(true)
    }

    override fun initProperties(state: Bundle?) {
        if (_location == null) {
            _location = createNewLocation()
            _location!!.externalSettings.isVisibleToUser = true
        } else _propertiesView!!.isInstantSave = true

        super.initProperties(state)
    }

    fun saveExternalSettings() {
        _location!!.saveExternalSettings()
    }

    override fun getTargetLocation(): LocationBase {
        return _location!!
    }

    override fun onPause() {
        super.onPause()
        if (_propertiesView!!.isInstantSave) {
            saveExternalSettings()
            LocationsManager.broadcastLocationChanged(activity, _location)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.create_location_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val mi = menu.findItem(R.id.confirm)
        mi.setVisible(!_propertiesView!!.isInstantSave)
        mi.setEnabled(isValidData)
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.confirm -> {
                addNewLocation()
                activity.finish()
                return true
            }

            else -> return super.onOptionsItemSelected(menuItem)
        }
    }

    protected val isValidData: Boolean
        get() = true

    protected abstract fun createNewLocation(): LocationBase?

    override fun createProperties() {
        _propertiesView!!.addProperty(object : TextPropertyEditor(
            this, R.string.title, 0,
            tag
        ) {
            override fun loadText(): String {
                return _location!!.title
            }

            override fun saveText(text: String) {
                _location!!.externalSettings.title = text.trim { it <= ' ' }
                if (propertiesView.isInstantSave) saveExternalSettings()
            }
        })
        val id = _propertiesView!!.addProperty(UseExternalFileManagerPropertyEditor(this))
        _propertiesView!!.setPropertyState(
            id,
            UserSettings.getSettings(activity).externalFileManagerInfo != null
        )
    }

    private var _location: LocationBase? = null

    private fun addNewLocation() {
        try {
            propertiesView.saveProperties()
            LocationsManager.getLocationsManager(activity).addNewLocation(_location, true)
            saveExternalSettings()
            LocationsManager.broadcastLocationAdded(activity, _location)
        } catch (e: Exception) {
            Logger.showAndLog(activity, e)
        }
    }
}
